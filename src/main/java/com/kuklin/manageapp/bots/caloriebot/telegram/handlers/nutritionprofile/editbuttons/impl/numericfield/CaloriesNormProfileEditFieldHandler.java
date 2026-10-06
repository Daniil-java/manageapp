package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class CaloriesNormProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public CaloriesNormProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(profileService, callbackHandler,
                2000,
                UserNutritionProfile.CALORIES_NORM_MIN,
                UserNutritionProfile.CALORIES_NORM_MAX,
                -500,
                -100,
                100,
                500
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getCaloriesNormPerDay();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        // Своя норма → ручной режим: вес и цель её больше не меняют, БЖУ пересчитаются от неё
        return profile
                .setNormMode(UserNutritionProfile.NormMode.MANUAL)
                .setCaloriesNormPerDay(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        StringBuilder sb = new StringBuilder(profile.getCaloriesNormPerDay() == null
                ? "Калории: не указаны"
                : "Калории: " + profile.getCaloriesNormPerDay() + " ккал"
                + (profile.isManualNorm() ? " (вручную)" : " (по формуле)"));

        Integer formula = profileService.calcFormulaCaloriesOrNull(profile);
        if (formula != null && profile.isManualNorm()) {
            sb.append("\nПо формуле: ").append(formula).append(" ккал");
        }
        sb.append("\n\nСохранённая норма выключает автопересчёт: она не меняется от веса и цели, БЖУ считаются от неё. ")
                .append("Вернуть расчёт по формуле — кнопка «Автопересчёт» в редактировании профиля.");
        return sb.toString();
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.CALORIE_NORM;
    }
}