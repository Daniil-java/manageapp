package com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation;

public class InvalidCaloriesNormException
        extends UserNutritionProfileValidationException {

    public InvalidCaloriesNormException(Integer calories) {
        super("Недопустимая норма калорий: " + calories);
    }
}
