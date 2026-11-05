package com.kuklin.manageapp.bots.caloriebot.profile.exception;

public class InvalidAgeException
        extends UserNutritionProfileValidationException {

    public InvalidAgeException(Integer age) {
        super("Недопустимый возраст: " + age);
    }
}
