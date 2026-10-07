package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProfileEditAction {
    SEX("SEX", "Пол", "Ваш пол: %s"),
    AGE("AGE", "Возраст", "Ваш возраст: %d"),
    HEIGHT("HEIGHT", "Рост", "Ваш рост: %d см"),
    CURRENT_WEIGHT("CUR_WEIGHT", "Текущий вес", "Ваш вес: %s кг"),
    ACTIVITY("ACTIVITY", "Активность", "Ваша активность: %s"),
    GOAL("GOAL", "Цель", "Ваша цель: %s"),
    DIET_TYPE("DIET_TYPE", "Тип питания", "Ваш тип диеты: %s"),
    CALORIE_NORM("CALORIES", "Норма калорий", "Ваша норма: %d ккал"),
    WATER_TARGET("WATER", "Норма воды", "Ваша норма воды: %d мл");

    private final String code;
    private final String label;
    private final String labelFormat;

    public static ProfileEditAction fromCode(String code) {
        if (code == null) return null;
        for (ProfileEditAction value : values()) {
            if (value.code.equals(code)) return value;
        }
        return null;
    }
}
