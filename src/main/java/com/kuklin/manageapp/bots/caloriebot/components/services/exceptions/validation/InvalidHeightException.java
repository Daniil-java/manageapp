package com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation;

public class InvalidHeightException
        extends UserNutritionProfileValidationException {

    public InvalidHeightException(Integer height) {
        super("Недопустимый рост: " + height);
    }
}