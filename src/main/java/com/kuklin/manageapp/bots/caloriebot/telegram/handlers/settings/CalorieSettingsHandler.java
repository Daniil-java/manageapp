package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;

/**
 * Интерфейс, который отвечает за параметр настроек
 */
public interface CalorieSettingsHandler extends CalorieBotUpdateHandler {

    /** Подпись кнопки в меню настроек — ключ словаря bot_*. */
    String getLabelKey();
}
