package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.springframework.stereotype.Component;

/**
 * Тексты про воду: строка «💧 [шкала] выпито / цель мл» (раньше — WaterEntry.getWaterStatusText).
 */
@Component
public class WaterTexts extends AbstractBotTexts {

    public WaterTexts(CalorieTexts texts) {
        super(texts);
    }

    /** «💧 [🟦🟦⬜…] 500 / 2000 мл». Цели нет — берём норму по умолчанию. */
    public String buildWaterStatusText(int currentWaterMl, Integer targetWaterMl, Long userId) {
        int totalBlocks = 10; // Длина шкалы

        if (targetWaterMl == null) {
            targetWaterMl = UserNutritionProfile.DEF_WATER_ML;
        }

        // Считаем количество закрашенных блоков
        int filledBlocks = (int) Math.round(((double) currentWaterMl / targetWaterMl) * totalBlocks);
        // Ограничиваем, чтобы не вышло за пределы 0-10
        filledBlocks = Math.max(0, Math.min(totalBlocks, filledBlocks));

        String bar = "🟦".repeat(filledBlocks) + "⬜".repeat(totalBlocks - filledBlocks);
        return texts.get(languageOf(userId), BotTextKeys.WATER_STATUS,
                bar, String.valueOf(currentWaterMl), String.valueOf(targetWaterMl));
    }
}
