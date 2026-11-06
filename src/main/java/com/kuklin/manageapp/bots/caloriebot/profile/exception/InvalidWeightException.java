package com.kuklin.manageapp.bots.caloriebot.profile.exception;

import java.math.BigDecimal;

public class InvalidWeightException
        extends UserNutritionProfileValidationException {

    public InvalidWeightException(BigDecimal weight) {
        super("Недопустимый вес: " + weight);
    }
}
