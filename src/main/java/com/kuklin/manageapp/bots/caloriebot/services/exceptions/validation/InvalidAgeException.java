package com.kuklin.manageapp.bots.caloriebot.services.exceptions.validation;

public class InvalidAgeException
        extends UserNutritionProfileValidationException {

    public InvalidAgeException(Integer age) {
        super("Недопустимый возраст: " + age);
    }
}
