package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WaterEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
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

        StringBuilder sb = new StringBuilder();

        // 1. Название и прибавка калорий
        String emoji = dish.getEmojiIcon() == null ? "🍽️" : dish.getEmojiIcon();
        sb.append(emoji).append("<b><i>").append(dish.getName()).append("</i></b> ")
                .append("(+").append(dish.getCalories()).append(" ккал)\n");

        // 2. БЖУ итоги всегда, с прибавками
        sb.append("<b>Б:</b> ").append(totalProteins);
        if (hasTargets) {
            sb.append("/").append(profile.getProteinsNormGramsPerDay());
        }
        sb.append("г (<b>+").append(dish.getProteins()).append("</b>) | ");

        sb.append("<b>Ж:</b> ").append(totalFats);
        if (hasTargets) {
            sb.append("/").append(profile.getFatsNormGramsPerDay());
        }
        sb.append("г (<b>+").append(dish.getFats()).append("</b>) | ");

        sb.append("<b>У:</b> ").append(totalCarbs);
        if (hasTargets) {
            sb.append("/").append(profile.getCarbsNormGramsPerDay());
        }
        sb.append("г (<b>+").append(dish.getCarbohydrates()).append("</b>)\n");
        sb.append("<b>Вес порции:</b> ").append(dish.getPortionWeight()).append(" г\n");
        sb.append("<b>Количество порций:</b> ").append(dish.getPortions()).append("\n\n");

        if (hasTargets) {
            int norm = profile.getCaloriesNormPerDay();
            int prevTotal = totalCalories - dish.getCalories();
            int totalPercent = (int) (((double) totalCalories / norm) * 100);
            int itemPercent = (int) (((double) dish.getCalories() / norm) * 100);

            // гарантируем +1%, если калории в блюде есть
            if (itemPercent == 0 && dish.getCalories() > 0) itemPercent = 1;

            // 3. Строка калорий и процент блюда
            sb.append(totalCalories).append("/").append(norm).append(" ")
                    .append("<b>+").append(dish.getCalories()).append(" ккал</b> ")
                    .append(" (<b>+").append(itemPercent).append("%</b>)\n");

            // 4. Прогресс-бар и итоговый %
            sb.append(generateSmartBar(prevTotal, dish.getCalories(), norm))
                    .append(" 🔥 <b>").append(totalPercent).append("%</b>");
        } else {
            // когда целей нет — просто суммарные калории
            sb.append("📊 Итого за сегодня: <b>").append(totalCalories).append(" ккал</b>\n");
        }

        return sb.toString();
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

        if (weightEntries.isEmpty()) {
            return "⚖️ История веса пока пуста.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⚖️ <b>ИСТОРИЯ ВЕСА</b>\n");
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

            sb.append("• ").append(current.getEntryDate())
                    .append(time)
                    .append(": <b>").append(current.getWeight()).append(" кг</b>")
                    .append(trend).append("\n");
        }

        if (weightEntries.size() > 1) {
            WeightEntry latest = weightEntries.get(weightEntries.size() - 1);
            WeightEntry first = weightEntries.get(0);
            double totalDiff = latest.getWeight().subtract(first.getWeight()).doubleValue();

            sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
            String progEmoji = totalDiff <= 0 ? "✅" : "⚠️";
            sb.append(progEmoji).append(" <b>Общий прогресс:</b> ")
                    .append(totalDiff > 0 ? "+" : "").append(String.format("%.2f", totalDiff)).append(" кг");
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

            return "🔥 К: " + generateButtonBar(total, norm, "\uD83D\uDFE7", "⬜") + " " + percent + "%";
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

            return "🥩 Б: " + generateButtonBar(total, norm, "🟪", "⬜") + " " + percent + "%";
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

            return "🥑 Ж: " + generateButtonBar(total, norm, "🟨", "⬜") + " " + percent + "%";
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

            return "🍞 У: " + generateButtonBar(total, norm, "\uD83D\uDFE9", "⬜") + " " + percent + "%";
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
            return "💧 В: " + generateButtonBar(total, norm, "\uD83D\uDFE6", "⬜") + " " + percent + "%";
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
