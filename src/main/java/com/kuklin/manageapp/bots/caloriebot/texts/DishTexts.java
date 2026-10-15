package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Тексты про блюда: карточка блюда после распознавания, дневник «Сегодня», ошибки распознавания с параметрами.
 * Карточка и дневник раньше собирались в AnalyticsService.
 */
@Component
public class DishTexts extends AbstractBotTexts {
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;

    public DishTexts(CalorieTexts texts, DishService dishService, UserNutritionProfileService userNutritionProfileService) {
        super(texts);
        this.dishService = dishService;
        this.userNutritionProfileService = userNutritionProfileService;
    }

    /**
     * Карточка блюда: данные блюда + итоги КБЖУ за день в сравнении с нормой.
     */
    public String buildDishCardText(Dish dish, Long userId) {
        return buildDishCardTexts(List.of(dish), userId).get(0);
    }

    /**
     * Карточки нескольких блюд (одно фото — несколько блюд). Блюда и профиль за день читаем один раз на все карточки:
     * все блюда уже сохранены, итоги дня у карточек одинаковые.
     */
    public List<String> buildDishCardTexts(List<Dish> dishes, Long userId) {
        List<Dish> today = dishService.getTodayDishes(userId);
        DayTotals totals = new DayTotals(
                today.stream().mapToInt(d -> nvl(d.getCalories())).sum(),
                today.stream().mapToInt(d -> nvl(d.getProteins())).sum(),
                today.stream().mapToInt(d -> nvl(d.getFats())).sum(),
                today.stream().mapToInt(d -> nvl(d.getCarbohydrates())).sum());
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        boolean hasTargets = userNutritionProfileService.checkTargetCalculateParams(profile);
        AppLanguage language = languageOf(userId);

        return dishes.stream().map(dish -> buildDishCardText(dish, totals, profile, hasTargets, language)).toList();
    }

    private record DayTotals(int calories, int proteins, int fats, int carbs) {
    }

    private String buildDishCardText(Dish dish, DayTotals totals, UserNutritionProfile profile,
                                     boolean hasTargets, AppLanguage language) {
        int totalCalories = totals.calories(), totalProteins = totals.proteins();
        int totalFats = totals.fats(), totalCarbs = totals.carbs();
        StringBuilder sb = new StringBuilder();

        // 1. Название и прибавка калорий
        String emoji = dish.getEmojiIcon() == null ? "🍽️" : dish.getEmojiIcon();
        sb.append(texts.get(language, BotTextKeys.DISH_CARD_TITLE,
                emoji, dish.getName(), String.valueOf(dish.getCalories()))).append("\n");

        // 2. БЖУ итоги всегда, с прибавками
        sb.append(texts.get(language, BotTextKeys.DISH_CARD_MACROS,
                getEatenText(totalProteins, hasTargets ? profile.getProteinsNormGramsPerDay() : null),
                String.valueOf(dish.getProteins()),
                getEatenText(totalFats, hasTargets ? profile.getFatsNormGramsPerDay() : null),
                String.valueOf(dish.getFats()),
                getEatenText(totalCarbs, hasTargets ? profile.getCarbsNormGramsPerDay() : null),
                String.valueOf(dish.getCarbohydrates()))).append("\n");
        sb.append(texts.get(language, BotTextKeys.DISH_CARD_PORTION_WEIGHT, String.valueOf(dish.getPortionWeight())))
                .append("\n");
        sb.append(texts.get(language, BotTextKeys.DISH_CARD_PORTIONS, String.valueOf(dish.getPortions())))
                .append("\n\n");

        if (hasTargets) {
            int norm = profile.getCaloriesNormPerDay();
            int prevTotal = totalCalories - dish.getCalories();
            int totalPercent = (int) (((double) totalCalories / norm) * 100);
            int itemPercent = (int) (((double) dish.getCalories() / norm) * 100);

            // гарантируем +1%, если калории в блюде есть
            if (itemPercent == 0 && dish.getCalories() > 0) itemPercent = 1;

            // 3. Строка калорий и процент блюда
            sb.append(texts.get(language, BotTextKeys.DISH_CARD_CALORIES_PROGRESS,
                    String.valueOf(totalCalories), String.valueOf(norm),
                    String.valueOf(dish.getCalories()), String.valueOf(itemPercent))).append("\n");

            // 4. Прогресс-бар и итоговый %
            sb.append(generateSmartBar(prevTotal, dish.getCalories(), norm))
                    .append(" 🔥 <b>").append(totalPercent).append("%</b>");
        } else {
            // когда целей нет — просто суммарные калории
            sb.append(texts.get(language, BotTextKeys.DISH_CARD_TODAY_TOTAL, String.valueOf(totalCalories)))
                    .append("\n");
        }

        return sb.toString();
    }

    /**
     * Дневник питания за сегодня: блюда и итоги КБЖУ против нормы.
     * date — заголовок дня, уже на языке пользователя («12 ОКТЯБРЯ 2026»).
     */
    public String buildTodayDiaryText(Long userId, String date) {
        AppLanguage language = languageOf(userId);
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        StringBuilder sb = new StringBuilder();
        sb.append(texts.get(language, BotTextKeys.TODAY_DIARY_TITLE, date)).append("\n\n");

        int cal = 0, fats = 0, proteins = 0, carbs = 0;

        for (Dish dish : dishes) {
            sb.append(buildDiaryDishText(dish, language)).append("\n");

            if (dish.getCalories() != null) cal += dish.getCalories();
            if (dish.getFats() != null) fats += dish.getFats();
            if (dish.getProteins() != null) proteins += dish.getProteins();
            if (dish.getCarbohydrates() != null) carbs += dish.getCarbohydrates();
        }

        sb.append("\n").append(texts.get(language, BotTextKeys.TODAY_TOTAL_TITLE)).append("\n");

        String kcal = texts.get(language, BotTextKeys.COMMON_UNIT_KCAL);
        String gram = texts.get(language, BotTextKeys.COMMON_UNIT_GRAM);
        appendTotal(sb, texts.get(language, BotTextKeys.MACRO_LABEL_CALORIES), cal, profile.getCaloriesNormPerDay(), kcal);
        appendTotal(sb, texts.get(language, BotTextKeys.MACRO_LABEL_PROTEINS), proteins, profile.getProteinsNormGramsPerDay(), gram);
        appendTotal(sb, texts.get(language, BotTextKeys.MACRO_LABEL_FATS), fats, profile.getFatsNormGramsPerDay(), gram);
        appendTotal(sb, texts.get(language, BotTextKeys.MACRO_LABEL_CARBS), carbs, profile.getCarbsNormGramsPerDay(), gram);

        return sb.toString();
    }

    /** «Голосовое слишком длинное — максимум 2 мин…». */
    public String getVoiceTooLongText(Long userId, Duration maxDuration) {
        AppLanguage language = languageOf(userId);
        return texts.get(language, BotTextKeys.DISH_VOICE_TOO_LONG, getDurationText(maxDuration, language));
    }

    /** Одно блюдо в дневнике: название, КБЖУ, вес или порции, уверенность ИИ. */
    private String buildDiaryDishText(Dish dish, AppLanguage language) {
        if (dish == null) {
            return texts.get(language, BotTextKeys.TODAY_DISH_NO_DATA);
        }

        String icon = dish.getEmojiIcon() != null ? dish.getEmojiIcon() : "🍽";

        StringBuilder sb = new StringBuilder();

        sb.append(icon)
                .append(" <b>")
                .append(dish.getName() != null ? dish.getName() : texts.get(language, BotTextKeys.TODAY_DISH_NO_NAME))
                .append("</b>\n")
                .append(texts.get(language, BotTextKeys.TODAY_DISH_MACROS,
                        String.valueOf(nvl(dish.getCalories())), String.valueOf(nvl(dish.getProteins())),
                        String.valueOf(nvl(dish.getFats())), String.valueOf(nvl(dish.getCarbohydrates()))))
                .append("\n");

        if (dish.getWeight() != null) {
            sb.append(texts.get(language, BotTextKeys.TODAY_DISH_WEIGHT, String.valueOf(dish.getWeight()))).append("\n");
        } else if (dish.getPortions() != null) {
            sb.append(texts.get(language, BotTextKeys.TODAY_DISH_PORTIONS, String.valueOf(dish.getPortions()))).append("\n");
        }

        if (dish.getAiConfidence() != null) {
            sb.append(texts.get(language, BotTextKeys.TODAY_DISH_AI_CONFIDENCE, String.valueOf(dish.getAiConfidence())))
                    .append("\n");
        }

        return sb.toString();
    }

    /** «30/120» — съедено и норма, или просто «30», если нормы нет. */
    private static String getEatenText(int eaten, Integer norm) {
        return norm == null ? String.valueOf(eaten) : eaten + "/" + norm;
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }

    private static void appendTotal(StringBuilder sb, String label, int total, Integer norm, String unit) {
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

    private static String generateSmartBar(int previous, int current, int norm) {
        int totalBars = 10;
        int totalCalories = previous + current;

        // 1. Если норма превышена — показываем "тревожную" шкалу
        if (totalCalories > norm) {
            StringBuilder overBar = new StringBuilder();
            // Ограничиваем 10-ю символами, но заменяем синий/зеленый на красный
            for (int i = 0; i < totalBars; i++) {
                overBar.append("🟥");
            }
            return overBar.toString();
        }

        // 2. Стандартная логика (3 цвета)
        int filledPrev = (int) Math.round((double) previous / norm * totalBars);
        int filledCurrent = (int) Math.round((double) current / norm * totalBars);

        // Гарантируем видимость текущего блюда (минимум 1 зеленый сегмент)
        if (current > 0 && filledCurrent == 0) filledCurrent = 1;

        // Чтобы не выскочить за 10 сегментов
        if (filledPrev + filledCurrent > totalBars) {
            filledCurrent = totalBars - filledPrev;
        }

        StringBuilder bar = new StringBuilder();
        for (int i = 1; i <= totalBars; i++) {
            if (i <= filledPrev) {
                bar.append("🟦"); // Было съедено
            } else if (i <= (filledPrev + filledCurrent)) {
                bar.append("🟪"); // Текущее блюдо
            } else {
                bar.append("⬜"); // Пусто
            }
        }
        return bar.toString();
    }
}
