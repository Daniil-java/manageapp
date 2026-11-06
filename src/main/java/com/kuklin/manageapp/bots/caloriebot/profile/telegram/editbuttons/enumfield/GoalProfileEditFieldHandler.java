package com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.enumfield;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.editbuttons.ProfileEditAction;
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
    protected Class<UserNutritionProfile.Goal> getEnumClass() {
        return UserNutritionProfile.Goal.class;
    }

    @Override
    protected UserNutritionProfile.Goal getCurrentValue(UserNutritionProfile profile) {
        return profile.getGoal();
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