package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Данные пользователя, которые уходят в ИИ для инсайтов (саммари и шаблоны поведения).
 * Только нужные ИИ поля — без id, служебных полей и с временем уже в таймзоне пользователя.
 */
public record InsightPayloadRecord(InsightProfile profile,
                                   List<InsightDish> dishes,
                                   List<InsightWeight> weights) {

    /** Сколько последних взвешиваний отдаём ИИ (могут быть и раньше периода — для динамики) */
    private static final int WEIGHTS_LIMIT = 20;

    public static InsightPayloadRecord of(UserNutritionProfile profile,
                                          List<Dish> dishes,
                                          List<WeightEntry> weights,
                                          ZoneId zoneId) {
        return new InsightPayloadRecord(
                InsightProfile.of(profile),
                dishes.stream()
                        .sorted(Comparator.comparing(Dish::getCreated, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(dish -> InsightDish.of(dish, zoneId))
                        .toList(),
                lastWeights(weights)
        );
    }

    private static List<InsightWeight> lastWeights(List<WeightEntry> weights) {
        return weights.stream()
                .filter(w -> w.getEntryDate() != null && w.getWeight() != null)
                .sorted(Comparator.comparing(WeightEntry::getEntryDate).reversed())
                .limit(WEIGHTS_LIMIT)
                .sorted(Comparator.comparing(WeightEntry::getEntryDate))
                .map(w -> new InsightWeight(w.getEntryDate(), w.getWeight()))
                .toList();
    }

    /** Профиль и цели по КБЖУ */
    public record InsightProfile(UserNutritionProfile.Sex sex,
                                 Integer ageYears,
                                 Integer heightCm,
                                 BigDecimal currentWeightKg,
                                 UserNutritionProfile.Goal goal,
                                 UserNutritionProfile.ActivityLevel activityLevel,
                                 UserNutritionProfile.DietType dietType,
                                 Integer caloriesNormPerDay,
                                 Integer proteinsNormGramsPerDay,
                                 Integer fatsNormGramsPerDay,
                                 Integer carbsNormGramsPerDay) {

        static InsightProfile of(UserNutritionProfile p) {
            return new InsightProfile(
                    p.getSex(), p.getAgeYears(), p.getHeightCm(), p.getCurrentWeightKg(),
                    p.getGoal(), p.getActivityLevel(), p.getDietType(),
                    p.getCaloriesNormPerDay(), p.getProteinsNormGramsPerDay(),
                    p.getFatsNormGramsPerDay(), p.getCarbsNormGramsPerDay()
            );
        }
    }

    /** Блюдо; eatenAt — локальное время пользователя */
    public record InsightDish(LocalDateTime eatenAt,
                              String name,
                              Dish.FoodCategory category,
                              Integer calories,
                              Integer proteins,
                              Integer fats,
                              Integer carbohydrates,
                              Integer weightGrams) {

        static InsightDish of(Dish d, ZoneId zoneId) {
            return new InsightDish(
                    d.getCreated() != null
                            ? d.getCreated().atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
                            : null,
                    d.getName(), d.getCategory(),
                    d.getCalories(), d.getProteins(), d.getFats(), d.getCarbohydrates(),
                    d.getWeight()
            );
        }
    }

    public record InsightWeight(LocalDate date, BigDecimal weightKg) {
    }
}
