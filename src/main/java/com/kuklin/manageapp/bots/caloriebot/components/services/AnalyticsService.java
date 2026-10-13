package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * Подсчёты по питанию и воде за сегодня и шкалы прогресса для кнопок «Сегодня».
 * Тексты (карточка блюда, дневник, история веса) — в пакете texts.
 */
public class AnalyticsService {
    private final WaterEntryService waterEntryService;
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieTexts texts;

    /**
     * Возвращает суммарный объем воды, выпитый пользователем за сегодня.
     */
    public Integer getTodayWaterMl(Long userId) {
        return waterEntryService.getTodayTotal(userId);
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
