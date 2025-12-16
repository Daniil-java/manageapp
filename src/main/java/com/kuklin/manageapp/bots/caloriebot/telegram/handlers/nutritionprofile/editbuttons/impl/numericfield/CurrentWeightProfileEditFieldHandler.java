package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CurrentWeightProfileEditFieldHandler
        extends AbstractDecimalProfileEditFieldHandler {

    public CurrentWeightProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(
                profileService,
                callbackHandler,
                new BigDecimal("70.0"),
                new BigDecimal("30.0"),
                new BigDecimal("300.0"),
                new BigDecimal("-5.0"),
                new BigDecimal("-0.5"),
                new BigDecimal("0.5"),
                new BigDecimal("5.0")
        );
    }

    @Override
    protected BigDecimal getCurrentValue(UserNutritionProfile profile) {
        return profile.getCurrentWeightKg();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, BigDecimal value) {
        return profile.setCurrentWeightKg(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        return profile.getCurrentWeightKg() == null
                ? "Вес: не указан"
                : "Вес: " + profile.getCurrentWeightKg().toPlainString() + " кг";
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.CURRENT_WEIGHT;
    }
}
