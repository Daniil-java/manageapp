package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProfileEditAction {
    SEX("SEX", "Пол"),
    AGE("AGE", "Возраст"),
    HEIGHT("HEIGHT", "Рост"),
    CURRENT_WEIGHT("CUR_WEIGHT", "Текущий вес"),
    ACTIVITY("ACTIVITY", "Активность"),
    GOAL("GOAL", "Цель"),
    DIET_TYPE("DIET_TYPE", "Тип питания"),
    CALORIE_NORM("CALORIES", "Норма калорий"),
    WATER_TARGET("WATER", "Норма воды");

    private final String code;
    private final String label;

    public static ProfileEditAction fromCode(String code) {
        if (code == null) return null;
        for (ProfileEditAction value : values()) {
            if (value.code.equals(code)) return value;
        }
        return null;
    }
}
