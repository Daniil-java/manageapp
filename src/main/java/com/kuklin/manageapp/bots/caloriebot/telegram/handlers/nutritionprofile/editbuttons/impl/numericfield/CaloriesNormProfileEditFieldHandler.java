package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
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
                800,
                6000,
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
        return profile.setCaloriesNormPerDay(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        return profile.getCaloriesNormPerDay() == null
                ? "Калории: не указаны"
                : "Калории: " + profile.getCaloriesNormPerDay() + " ккал";
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.CALORIE_NORM;
    }
}