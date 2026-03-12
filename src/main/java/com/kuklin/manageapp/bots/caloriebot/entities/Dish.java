package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "dishes")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Основное ---
    private String name;
    private String emojiIcon;          // 🍕 🥗 ☕️ — для UI

    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;

    // --- Количество ---
    private Integer weightGrams;       // фактический вес блюда
    private Integer portions;          // количество порций
    private Integer portionWeight;     // вес одной порции (если известно)

    // --- Классификация ---
    @Enumerated(EnumType.STRING)
    private FoodCategory category;           // fast_food / home_food / dessert / etc

    // --- ИИ ---
    private Integer aiConfidence;      // 0–100, насколько ИИ уверен

    // --- Системное ---
    private Long userId;

    @CreationTimestamp
    private Instant created;

    @Getter
    @RequiredArgsConstructor
    public enum FoodCategory {

        MAIN_COURSE("Основное блюдо"),
        SIDE_DISH("Гарнир"),
        SOUP("Суп"),
        SALAD("Салат"),
        SNACK("Снэк / перекус"),
        DESSERT("Десерт"),
        DRINK("Напиток"),
        BREAKFAST_ITEM("Блюдо для завтрака"),
        BREAD_BAKERY("Хлеб / выпечка"),
        FAST_FOOD("Фастфуд"),
        SAUCE_DIP("Соус / дип"),
        UNKNOWN("Неизвестно");

        private final String name;
    }


    public static Dish toEntity(DishDto dto) {
        return new Dish()
                .setName(dto.getName())
                .setEmojiIcon(dto.getEmojiIcon())
                .setCalories(dto.getCalories())
                .setProteins(dto.getProteins())
                .setFats(dto.getFats())
                .setCarbohydrates(dto.getCarbohydrates())
                .setWeightGrams(dto.getWeightGrams())
                .setPortions(dto.getPortions())
                .setPortionWeight(dto.getPortionWeight())
                .setCategory(dto.getCategory())
                .setAiConfidence(dto.getAiConfidence())
                .setUserId(dto.getUserId());
    }

    public static String getInfo(Dish dish) {
        if (dish == null) {
            return "❌ Нет данных о блюде";
        }

        String icon = dish.getEmojiIcon() != null ? dish.getEmojiIcon() : "🍽";

        StringBuilder sb = new StringBuilder();

        sb.append(icon)
                .append(" <b>")
                .append(dish.getName() != null ? dish.getName() : "Без названия")
                .append("</b>\n")

                .append("🔥 Ккал: <b>").append(nvl(dish.getCalories())).append("</b> ")
                .append("🥩 Б: <b>").append(nvl(dish.getProteins())).append(" г</b> ")
                .append("🥑 Ж: <b>").append(nvl(dish.getFats())).append(" г</b> ")
                .append("🍞 У: <b>").append(nvl(dish.getCarbohydrates())).append(" г</b>\n");

        if (dish.getWeightGrams() != null) {
            sb.append("⚖️ Вес: <b>").append(dish.getWeightGrams()).append(" г</b>\n");
        } else if (dish.getPortions() != null) {
            sb.append("🍽 Порции: <b>").append(dish.getPortions()).append("</b>\n");
        }

        if (dish.getAiConfidence() != null) {
            sb.append("🤖 Уверенность ИИ: <b>")
                    .append(dish.getAiConfidence()).append("%</b>\n");
        }

        return sb.toString();
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Смещает КБЖУ на заданный процент.
     * percentDelta: -50, -10, 10, 50 и т.п.
     */
    public Dish applyPercentDelta(int percentDelta) {
        double multiplier = 1.0 + (percentDelta / 100.0);
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        this.calories      = scale(this.calories, multiplier);
        this.proteins      = scale(this.proteins, multiplier);
        this.fats          = scale(this.fats, multiplier);
        this.carbohydrates = scale(this.carbohydrates, multiplier);

        return this;
    }

    public Dish applyPortionWeightPercentDelta(int percentDelta) {
        if (portionWeight == null || portions == null || portions <= 0) {
            // Нечего пересчитывать — данных нет
            return this;
        }

        double multiplier = 1.0 + percentDelta / 100.0;
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        // 1. Меняем вес одной порции
        this.portionWeight = scale(this.portionWeight, multiplier);

        // 2. Пересчитываем общий вес
        this.weightGrams = this.portionWeight * this.portions;

        // 3. Масштабируем КБЖУ
        this.calories      = scale(this.calories, multiplier);
        this.proteins      = scale(this.proteins, multiplier);
        this.fats          = scale(this.fats, multiplier);
        this.carbohydrates = scale(this.carbohydrates, multiplier);

        return this;
    }

    public static Integer scale(Integer value, double multiplier) {
        if (value == null) {
            return null;
        }

        long result = Math.round(value * multiplier);

        if (result < 0) {
            result = 0;
        } else if (result > Integer.MAX_VALUE) {
            result = Integer.MAX_VALUE;
        }

        return (int) result;
    }

    public static String getDishesString(List<Dish> dishes, UserNutritionProfile profile, String date) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 <b>Дневник питания ("+ date + ")</b>\n\n");

        int cal = 0, fats = 0, proteins = 0, carbs = 0;

        for (Dish dish : dishes) {
            sb.append(Dish.getInfo(dish)).append("\n");

            if (dish.getCalories() != null) cal += dish.getCalories();
            if (dish.getFats() != null) fats += dish.getFats();
            if (dish.getProteins() != null) proteins += dish.getProteins();
            if (dish.getCarbohydrates() != null) carbs += dish.getCarbohydrates();
        }

        sb.append("\n⚡️ <b>ИТОГО:</b>\n");

        appendTotal(sb, "🔥 К", cal, profile.getCaloriesNormPerDay(), "ккал");
        appendTotal(sb, "🥩 Б", proteins, profile.getProteinsNormGramsPerDay(), "г");
        appendTotal(sb, "🥑 Ж", fats, profile.getFatsNormGramsPerDay(), "г");
        appendTotal(sb, "🍞 У", carbs, profile.getCarbsNormGramsPerDay(), "г");

        return sb.toString();
    }

    private static void appendTotal(
            StringBuilder sb,
            String label,
            int total,
            Integer norm,
            String unit
    ) {
        sb.append(label)
                .append(": <b>")
                .append(total)
                .append("</b>");

        if (norm != null) {
            sb.append(" / ")
                    .append(norm)
                    .append(" ")
                    .append(unit);
        }

        sb.append("\n");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dish{");
        sb.append("name='").append(name).append("'");
        sb.append(", cal=").append(calories);
        sb.append(", p=").append(proteins);
        sb.append(", f=").append(fats);
        sb.append(", c=").append(carbohydrates);
        sb.append(", weight=").append(weightGrams);
        sb.append(", cat=").append(category);
        sb.append(", conf=").append(aiConfidence);
        sb.append(", created=").append(created); // Instant в формате UTC ISO-8601
        sb.append("}");
        return sb.toString();
    }

    public static String toStringList(List<Dish> dishes) {
        if (dishes == null || dishes.isEmpty()) {
            return "dishes: empty";
        }

        return dishes.stream()
                .map(Dish::toString)
                .collect(Collectors.joining("\n"));
    }
}
