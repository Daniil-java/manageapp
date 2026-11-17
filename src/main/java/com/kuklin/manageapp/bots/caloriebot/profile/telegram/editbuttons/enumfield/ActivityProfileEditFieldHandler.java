package com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.enumfield;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class ActivityProfileEditFieldHandler
        extends AbstractEnumProfileEditFieldHandler<UserNutritionProfile.ActivityLevel> {

    public ActivityProfileEditFieldHandler(
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            UserNutritionProfileService profileService
    ) {
        super(callbackHandler, profileService);
    }

    @Override
    protected Class<UserNutritionProfile.ActivityLevel> getEnumClass() {
        return UserNutritionProfile.ActivityLevel.class;
    }

    @Override
    protected UserNutritionProfile.ActivityLevel getCurrentValue(UserNutritionProfile profile) {
        return profile.getActivityLevel();
    }

    @Override
    protected UserNutritionProfile applyValue(
            UserNutritionProfile profile,
            UserNutritionProfile.ActivityLevel value
    ) {
        return profile.setActivityLevel(value);
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.ACTIVITY;
    }
}
