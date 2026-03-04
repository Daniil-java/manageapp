package com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation;

import java.math.BigDecimal;

public class InvalidWeightException
        extends UserNutritionProfileValidationException {

    public InvalidWeightException(BigDecimal weight) {
        super("Недопустимый вес: " + weight);
    }
}
