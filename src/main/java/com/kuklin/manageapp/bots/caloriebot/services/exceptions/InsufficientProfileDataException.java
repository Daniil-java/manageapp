package com.kuklin.manageapp.bots.caloriebot.services.exceptions;

public class InsufficientProfileDataException
        extends UserNutritionProfileException {

    public InsufficientProfileDataException(String fieldName) {
        super("Недостаточно данных для расчёта. Поле не заполнено: " + fieldName);
    }
}
