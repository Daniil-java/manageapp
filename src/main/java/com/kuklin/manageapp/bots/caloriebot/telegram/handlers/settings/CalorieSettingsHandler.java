package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;

/**
 * Интерфейс, который отвечает за параметр настроек
 */
public interface CalorieSettingsHandler extends CalorieBotUpdateHandler {

    String getLabel();
}
