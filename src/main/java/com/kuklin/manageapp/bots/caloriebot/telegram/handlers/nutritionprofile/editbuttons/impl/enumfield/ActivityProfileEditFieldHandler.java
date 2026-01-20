package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.enumfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
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
    protected String getTitle() {
        return "Выберите уровень активности";
    }

    @Override
    protected Class<UserNutritionProfile.ActivityLevel> getEnumClass() {
        return UserNutritionProfile.ActivityLevel.class;
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
