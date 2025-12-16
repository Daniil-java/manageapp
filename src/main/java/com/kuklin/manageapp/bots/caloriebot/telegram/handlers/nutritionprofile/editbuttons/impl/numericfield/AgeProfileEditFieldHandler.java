package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class AgeProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public AgeProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(
                profileService,
                callbackHandler,
                20,     // default
                1,      // min
                130,    // max
                -5,     // bigNeg
                -1,     // neg
                1,      // pos
                5       // bigPos
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getAgeYears();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        return profile.setAgeYears(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        return profile.getAgeYears() == null
                ? "Возраст: не указан"
                : "Возраст: " + profile.getAgeYears();
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.AGE;
    }
}
