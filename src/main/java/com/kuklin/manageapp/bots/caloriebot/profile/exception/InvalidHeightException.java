package com.kuklin.manageapp.bots.caloriebot.profile.exception;

public class InvalidHeightException
        extends UserNutritionProfileValidationException {

    public InvalidHeightException(Integer height) {
        super("Недопустимый рост: " + height);
    }
}