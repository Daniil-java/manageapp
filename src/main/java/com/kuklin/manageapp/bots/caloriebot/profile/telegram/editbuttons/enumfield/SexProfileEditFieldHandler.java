package com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.enumfield;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class SexProfileEditFieldHandler
        extends AbstractEnumProfileEditFieldHandler<UserNutritionProfile.Sex> {

    public SexProfileEditFieldHandler(
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            UserNutritionProfileService profileService
    ) {
        super(callbackHandler, profileService);
    }

    @Override
    protected Class<UserNutritionProfile.Sex> getEnumClass() {
        return UserNutritionProfile.Sex.class;
    }

    @Override
    protected UserNutritionProfile.Sex getCurrentValue(UserNutritionProfile profile) {
        return profile.getSex();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, UserNutritionProfile.Sex value) {
        return profile.setSex(value);
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.SEX;
    }
}
