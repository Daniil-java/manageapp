package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * Сервис для анализа данных пользователя и формирования текстовых отчетов.
 * Объединяет информацию о питании, потреблении воды и динамике веса.
 */
public class AnalyticsService {
    private final WaterEntryService waterEntryService;
    private final WeightEntryService weightEntryService;
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserSettingsService userSettingsService;
    private final CalorieTextsService texts;

    /**
     * Возвращает суммарный объем воды, выпитый пользователем за сегодня.
     */
    public Integer getTodayWaterMl(Long userId) {
        return waterEntryService.getTodayTotal(userId);
    }

    /**
     * Формирует текстовый статус по потреблению воды (текущее vs цель).
     */
    public String getWaterStatusText(Long userId) {
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        Integer current = getTodayWaterMl(userId);
        Integer targetWaterMl = profile.getWaterTargetMlPerDay();

        return WaterEntry.getWaterStatusText(current, targetWaterMl);
    }

    /**
     * Формирует отчет по КБЖУ: данные конкретного блюда + итоги за день в сравнении с нормой.
     */
    public String getInfo(Dish dish, Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        int totalCalories = 0, totalProteins = 0, totalFats = 0, totalCarbs = 0;

        for (Dish d : dishes) {
            totalCalories += d.getCalories();
            totalProteins += d.getProteins();
            totalFats += d.getFats();
            totalCarbs += d.getCarbohydrates();
        }

        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        boolean hasTargets = userNutritionProfileService.checkTargetCalculateParams(profile);

        AppLanguage language = texts.languageOf(userId);
        StringBuilder sb = new StringBuilder();

        // Числа передаём строками: ICU иначе поставил бы разделители разрядов (1 850)
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

    /** «30/120» — съедено и норма, или просто «30», если нормы нет. */
    private static String getEatenText(int eaten, Integer norm) {
        return norm == null ? String.valueOf(eaten) : eaten + "/" + norm;
    }

    /**
     * Дневник питания за сегодня: блюда и итоги КБЖУ против нормы.
     * date — заголовок дня, уже на языке пользователя («12 ОКТЯБРЯ 2026»).
     */
    public String getTodayDiaryText(Long userId, String date) {
        AppLanguage language = texts.languageOf(userId);
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        StringBuilder sb = new StringBuilder();
        sb.append(texts.get(language, BotTextKeys.TODAY_DIARY_TITLE, date)).append("\n\n");

        int cal = 0, fats = 0, proteins = 0, carbs = 0;

        for (Dish dish : dishes) {
            sb.append(getDiaryDishText(dish, language)).append("\n");

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

    /** Одно блюдо в дневнике: название, КБЖУ, вес или порции, уверенность ИИ. */
    private String getDiaryDishText(Dish dish, AppLanguage language) {
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


    private String generateSmartBar(int previous, int current, int norm) {
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
                bar.append("\uD83D\uDFEA"); // Текущее яблоко
            } else {
                bar.append("⬜"); // Пусто
            }
        }
        return bar.toString();
    }

    /**
     * Формирует список истории взвешиваний с расчетом разницы между замерами и общим прогрессом.
     */
    public String getWeightHistoryTextList(Long userId) {
        // Получаем список, отсортированный от старых к новым (Asc)
        List<WeightEntry> weightEntries = weightEntryService.getAllWeightHistory(userId);

        AppLanguage language = texts.languageOf(userId);
        if (weightEntries.isEmpty()) {
            return texts.get(language, BotTextKeys.WEIGHT_HISTORY_EMPTY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_TITLE)).append("\n");
        sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");

        for (int i = 0; i < weightEntries.size(); i++) {
            WeightEntry current = weightEntries.get(i);
            String trend = "";

            // Теперь сравниваем с ПРЕДЫДУЩИМ элементом (i-1), так как идем от старых к новым
            if (i > 0) {
                WeightEntry previous = weightEntries.get(i - 1);
                double diff = current.getWeight().subtract(previous.getWeight()).doubleValue();

                if (diff > 0) {
                    trend = " 📈 <b>+" + String.format("%.1f", diff) + "</b>";
                } else if (diff < 0) {
                    trend = " 📉 <b>" + String.format("%.1f", diff) + "</b>";
                } else {
                    trend = " ↔️ 0.0";
                }
            }

            ZoneId zoneId = userSettingsService.getOrCreate(userId).getZoneId();
            // Добавляем время, если в БД есть createdAt, чтобы различать замеры в один день
            String time = current.getCreatedAt() != null
                    ? " (" + current.getCreatedAt()
                    .atZone(zoneId)
                    .format(DateTimeFormatter.ofPattern("HH:mm")) + ")"
                    : "";

            sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_ENTRY,
                    current.getEntryDate() + time, String.valueOf(current.getWeight()), trend)).append("\n");
        }

        if (weightEntries.size() > 1) {
            WeightEntry latest = weightEntries.get(weightEntries.size() - 1);
            WeightEntry first = weightEntries.get(0);
            double totalDiff = latest.getWeight().subtract(first.getWeight()).doubleValue();

            sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
            String progEmoji = totalDiff <= 0 ? "✅" : "⚠️";
            sb.append(texts.get(language, BotTextKeys.WEIGHT_HISTORY_TOTAL,
                    progEmoji, (totalDiff > 0 ? "+" : "") + String.format("%.2f", totalDiff)));
        }

        return sb.toString();
    }

    /**
     * Возвращает строку прогресса для кнопки ККАЛ (Синий + Фиолетовый)
     */
    public String getCaloriesBarButtonOrEmpty(Long userId) {
        try {
            UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
            List<Dish> dishes = dishService.getTodayDishes(userId);

            int total = dishes.stream().mapToInt(Dish::getCalories).sum();
            int norm = profile.getCaloriesNormPerDay();
            int percent = (int) (((double) total / norm) * 100);

            return texts.get(userId, BotTextKeys.MACRO_LABEL_CALORIES) + ": " + generateButtonBar(total, norm, "\uD83D\uDFE7", "⬜") + " " + percent + "%";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Возвращает строку прогресса для кнопки БЕЛКИ (Фиолетовый)
     */
    public String getProteinsBarButtonOrEmpty(Long userId) {
        try {
            UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
            int total = dishService.getTodayDishes(userId).stream().mapToInt(Dish::getProteins).sum();
            int norm = profile.getProteinsNormGramsPerDay();
            int percent = (int) (((double) total / norm) * 100);

            return texts.get(userId, BotTextKeys.MACRO_LABEL_PROTEINS) + ": " + generateButtonBar(total, norm, "🟪", "⬜") + " " + percent + "%";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Возвращает строку прогресса для кнопки ЖИРЫ (Желтый)
     */
    public String getFatsBarButtonOrEmpty(Long userId) {
        try {
            UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
            int total = dishService.getTodayDishes(userId).stream().mapToInt(Dish::getFats).sum();
            int norm = profile.getFatsNormGramsPerDay();
            int percent = (int) (((double) total / norm) * 100);

            return texts.get(userId, BotTextKeys.MACRO_LABEL_FATS) + ": " + generateButtonBar(total, norm, "🟨", "⬜") + " " + percent + "%";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Возвращает строку прогресса для кнопки УГЛЕВОДЫ (Синий/Коричневый)
     */
    public String getCarbsBarButton(Long userId) {
        try {
            UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
            int total = dishService.getTodayDishes(userId).stream().mapToInt(Dish::getCarbohydrates).sum();
            int norm = profile.getCarbsNormGramsPerDay();
            int percent = (int) (((double) total / norm) * 100);

            return texts.get(userId, BotTextKeys.MACRO_LABEL_CARBS) + ": " + generateButtonBar(total, norm, "\uD83D\uDFE9", "⬜") + " " + percent + "%";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Возвращает строку прогресса для кнопки ВОДА (Голубые круги)
     */
    public String getWaterBarButton(Long userId) {
        try {
            UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
            int total = getTodayWaterMl(userId);
            int norm = profile.getWaterTargetMlPerDay() == null ? 2000 : profile.getWaterTargetMlPerDay();

            int percent = (int) (((double) total / norm) * 100);

            // Используем круги для визуального отличия воды от еды
            return texts.get(userId, BotTextKeys.MACRO_LABEL_WATER) + ": " + generateButtonBar(total, norm, "\uD83D\uDFE6", "⬜") + " " + percent + "%";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Универсальный генератор короткой шкалы для кнопок (10 сегментов)
     */
    private String generateButtonBar(int current, int norm, String filledEmoji, String emptyEmoji) {
        int totalBars = 10; // Оптимально для Inline-кнопок
        if (norm <= 0) return emptyEmoji.repeat(totalBars);

        int filled = (int) Math.round((double) current / norm * totalBars);

        if (current > 0 && filled == 0) filled = 1; // Видимость минимального прогресса
        if (filled > totalBars) return "🟥".repeat(totalBars); // Индикатор перебора

        return filledEmoji.repeat(filled) + emptyEmoji.repeat(totalBars - filled);
    }
}
