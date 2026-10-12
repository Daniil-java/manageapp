package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Что редактируем в профиле. code — в callbackData, тексты — в словаре bot_*:
 * подпись кнопки getTextKey(), значение на кнопке-счётчике getValueKey(), заголовок выбора getChooseKey().
 */
@Getter
@AllArgsConstructor
public enum ProfileEditAction {
    SEX("SEX"),
    AGE("AGE"),
    HEIGHT("HEIGHT"),
    CURRENT_WEIGHT("CUR_WEIGHT"),
    ACTIVITY("ACTIVITY"),
    GOAL("GOAL"),
    DIET_TYPE("DIET_TYPE"),
    CALORIE_NORM("CALORIES"),
    WATER_TARGET("WATER");

    private final String code;

    /** Подпись кнопки в меню редактирования: «Пол», «Норма воды». */
    public String getTextKey() {
        return "profile.edit." + name();
    }

    /** Значение на кнопке-счётчике: «Ваш рост: {0} см». Есть только у числовых полей. */
    public String getValueKey() {
        return "profile.edit.value." + name();
    }

    /** Заголовок выбора из списка: «Выберите пол». Есть только у полей-перечислений. */
    public String getChooseKey() {
        return "profile.choose." + name();
    }

    public static ProfileEditAction fromCode(String code) {
        if (code == null) return null;
        for (ProfileEditAction value : values()) {
            if (value.code.equals(code)) return value;
        }
        return null;
    }
}
