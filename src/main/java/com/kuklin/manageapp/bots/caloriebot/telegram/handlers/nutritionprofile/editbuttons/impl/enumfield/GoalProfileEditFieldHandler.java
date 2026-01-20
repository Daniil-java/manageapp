package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.enumfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class GoalProfileEditFieldHandler
        extends AbstractEnumProfileEditFieldHandler<UserNutritionProfile.Goal> {

    public GoalProfileEditFieldHandler(
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler,
            UserNutritionProfileService profileService
    ) {
        super(callbackHandler, profileService);
    }

    @Override
    protected String getTitle() {
        return "Выберите цель";
    }

    @Override
    protected Class<UserNutritionProfile.Goal> getEnumClass() {
        return UserNutritionProfile.Goal.class;
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, UserNutritionProfile.Goal value) {
        return profile.setGoal(value);
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.GOAL;
    }
}