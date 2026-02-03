package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class WaterTargetProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public WaterTargetProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(profileService, callbackHandler,
                2000,
                500,
                6000,
                -500,
                -100,
                100,
                500
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getWaterTargetMlPerDay();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        return profile.setWaterTargetMlPerDay(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        return profile.getWaterTargetMlPerDay() == null
                ? "Вода: не указана"
                : "Вода: " + profile.getWaterTargetMlPerDay() + " мл";
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.WATER_TARGET;
    }
}