package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class HeightProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public HeightProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(profileService, callbackHandler,
                170,    // default
                50,     // min
                250,    // max
                -10,    // bigNeg
                -1,     // neg
                1,      // pos
                10      // bigPos
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getHeightCm();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        return profile.setHeightCm(value);
    }
    @Override
    protected String buildText(UserNutritionProfile profile) {
        return profile.getHeightCm() == null
                ? "Рост: не указан"
                : "Рост: " + profile.getHeightCm() + " см";
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.HEIGHT;
    }
}
