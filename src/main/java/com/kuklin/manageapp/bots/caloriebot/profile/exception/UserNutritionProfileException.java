package com.kuklin.manageapp.bots.caloriebot.profile.exception;

public abstract class UserNutritionProfileException extends Exception {

    protected UserNutritionProfileException(String message) {
        super(message);
    }

    protected UserNutritionProfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
