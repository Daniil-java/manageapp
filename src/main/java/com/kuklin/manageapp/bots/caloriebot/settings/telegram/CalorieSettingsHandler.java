package com.kuklin.manageapp.bots.caloriebot.settings.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;

/**
 * Интерфейс, который отвечает за параметр настроек
 */
public interface CalorieSettingsHandler extends CalorieBotUpdateHandler {

    /** Подпись кнопки в меню настроек — ключ словаря bot_*. */
    String getLabelKey();
}
