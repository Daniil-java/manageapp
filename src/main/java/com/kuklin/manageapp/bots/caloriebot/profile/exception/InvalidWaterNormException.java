package com.kuklin.manageapp.bots.caloriebot.profile.exception;

public class InvalidWaterNormException
        extends UserNutritionProfileValidationException {

    public InvalidWaterNormException(Integer waterMl) {
        super("Недопустимая норма воды: " + waterMl);
    }
}
