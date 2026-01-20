package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.enumfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
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
    protected String getTitle() {
        return "Выберите пол";
    }

    @Override
    protected Class<UserNutritionProfile.Sex> getEnumClass() {
        return UserNutritionProfile.Sex.class;
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
