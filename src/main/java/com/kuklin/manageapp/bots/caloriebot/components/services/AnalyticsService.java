package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
/**
 * Подсчёты по питанию и воде за сегодня и шкалы прогресса для кнопок «Сегодня».
 * Тексты (карточка блюда, дневник, история веса) — в пакете texts.
 */
public class AnalyticsService {
    private static final int DEFAULT_WATER_TARGET_ML = 2000;

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
     * Шкалы прогресса за сегодня: калории, белки, жиры, углеводы, вода — по строке на кнопку.
     * Профиль, блюда, воду и язык читаем один раз (раньше каждая шкала грузила всё заново — около 20 запросов).
     * Нормы нет (профиль не заполнен) — этой шкалы нет.
     */
    public List<String> buildTodayBars(Long userId) {
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        List<Dish> dishes = dishService.getTodayDishes(userId);
        AppLanguage language = texts.languageOf(userId);
        Integer waterTarget = profile.getWaterTargetMlPerDay();

        List<String> bars = new ArrayList<>();
        addBar(bars, language, BotTextKeys.MACRO_LABEL_CALORIES, sum(dishes, Dish::getCalories),
                profile.getCaloriesNormPerDay(), "🟧");
        addBar(bars, language, BotTextKeys.MACRO_LABEL_PROTEINS, sum(dishes, Dish::getProteins),
                profile.getProteinsNormGramsPerDay(), "🟪");
        addBar(bars, language, BotTextKeys.MACRO_LABEL_FATS, sum(dishes, Dish::getFats),
                profile.getFatsNormGramsPerDay(), "🟨");
        addBar(bars, language, BotTextKeys.MACRO_LABEL_CARBS, sum(dishes, Dish::getCarbohydrates),
                profile.getCarbsNormGramsPerDay(), "🟩");
        addBar(bars, language, BotTextKeys.MACRO_LABEL_WATER, getTodayWaterMl(userId),
                waterTarget == null ? DEFAULT_WATER_TARGET_ML : waterTarget, "🟦");
        return bars;
    }

    private void addBar(List<String> bars, AppLanguage language, String labelKey, int total, Integer norm, String filledEmoji) {
        if (norm == null || norm <= 0) return;
        int percent = (int) (((double) total / norm) * 100);
        bars.add(texts.get(language, labelKey) + ": " + generateButtonBar(total, norm, filledEmoji, "⬜") + " " + percent + "%");
    }

    private static int sum(List<Dish> dishes, Function<Dish, Integer> value) {
        return dishes.stream().map(value).mapToInt(v -> v == null ? 0 : v).sum();
    }

    /**
     * Универсальный генератор короткой шкалы для кнопок (10 сегментов)
     */
    private String generateButtonBar(int current, int norm, String filledEmoji, String emptyEmoji) {
        int totalBars = 10; // Оптимально для Inline-кнопок

        int filled = (int) Math.round((double) current / norm * totalBars);

        if (current > 0 && filled == 0) filled = 1; // Видимость минимального прогресса
        if (filled > totalBars) return "🟥".repeat(totalBars); // Индикатор перебора

        return filledEmoji.repeat(filled) + emptyEmoji.repeat(totalBars - filled);
    }
}
