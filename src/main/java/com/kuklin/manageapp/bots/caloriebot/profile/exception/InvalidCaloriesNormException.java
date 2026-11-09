package com.kuklin.manageapp.bots.caloriebot.profile.exception;

public class InvalidCaloriesNormException
        extends UserNutritionProfileValidationException {

    public InvalidCaloriesNormException(Integer calories) {
        super("Недопустимая норма калорий: " + calories);
    }
}
