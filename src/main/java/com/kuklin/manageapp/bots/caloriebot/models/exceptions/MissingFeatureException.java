package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;

/**
 * Исключение, выбрасываемое при попытке доступа к функции, на которую у пользователя
 * нет прав или исчерпан лимит использований.
 */
public class MissingFeatureException extends Exception {

    /** Тип функции, доступ к которой был отклонен */
    private final BotFeature requiredFeature;

    /**
     * Конструктор исключения.
     * @param feature функциональность бота, вызвавшая отказ в доступе.
     */
    public MissingFeatureException(BotFeature feature) {
        super("Access denied. Required feature: " + feature);
        this.requiredFeature = feature;
    }

    /**
     * Возвращает фичу, из-за которой произошло исключение.
     * Полезно для формирования персонализированного ответа пользователю (например, кнопки "Купить подписку").
     */
    public BotFeature getRequiredFeature() {
        return requiredFeature;
    }
}
