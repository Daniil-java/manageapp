package com.kuklin.manageapp.bots.caloriebot.components.services.exceptions;

public abstract class UserNutritionProfileException extends Exception {

    protected UserNutritionProfileException(String message) {
        super(message);
    }

    protected UserNutritionProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
