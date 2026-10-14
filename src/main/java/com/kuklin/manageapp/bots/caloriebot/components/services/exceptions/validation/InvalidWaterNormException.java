package com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation;

public class InvalidWaterNormException
        extends UserNutritionProfileValidationException {

    public InvalidWaterNormException(Integer waterMl) {
        super("Недопустимая норма воды: " + waterMl);
    }
}
