package com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.enumfield;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class DietProfileEditFieldHandler
        extends AbstractEnumProfileEditFieldHandler<UserNutritionProfile.DietType> {

    public DietProfileEditFieldHandler(
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            UserNutritionProfileService profileService
    ) {
        super(callbackHandler, profileService);
    }

    @Override
    protected Class<UserNutritionProfile.DietType> getEnumClass() {
        return UserNutritionProfile.DietType.class;
    }

    @Override
    protected UserNutritionProfile.DietType getCurrentValue(UserNutritionProfile profile) {
        return profile.getDietType();
    }

    @Override
    protected UserNutritionProfile applyValue(
            UserNutritionProfile profile,
            UserNutritionProfile.DietType value
    ) {
        return profile.setDietType(value);
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.DIET_TYPE;
    }
}
