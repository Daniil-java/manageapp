package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.enumfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
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
    protected String getTitle() {
        return "Выберите тип питания";
    }

    @Override
    protected Class<UserNutritionProfile.DietType> getEnumClass() {
        return UserNutritionProfile.DietType.class;
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
