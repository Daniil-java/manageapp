package com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.numericfield;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.ProfileEditAction;
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
                1,      // small
                5,      // mid
                10      // big
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
                ? texts.get(profile.getUserId(), BotTextKeys.PROFILE_FIELD_HEIGHT_EMPTY)
                : texts.get(profile.getUserId(), BotTextKeys.PROFILE_FIELD_HEIGHT, String.valueOf(profile.getHeightCm()));
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.HEIGHT;
    }
}
