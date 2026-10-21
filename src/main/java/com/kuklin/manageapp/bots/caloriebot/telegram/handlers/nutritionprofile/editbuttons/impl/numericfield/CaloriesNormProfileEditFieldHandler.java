package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import org.springframework.stereotype.Component;

@Component
public class CaloriesNormProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public CaloriesNormProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(profileService, callbackHandler,
                2000,
                UserNutritionProfile.CALORIES_NORM_MIN,
                UserNutritionProfile.CALORIES_NORM_MAX,
                -500,
                -100,
                100,
                500
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getCaloriesNormPerDay();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        // Своя норма → ручной режим: вес и цель её больше не меняют, БЖУ пересчитаются от неё
        return profile
                .setNormMode(UserNutritionProfile.NormMode.MANUAL)
                .setCaloriesNormPerDay(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        AppLanguage language = texts.languageOf(profile.getUserId());
        StringBuilder sb = new StringBuilder(profile.getCaloriesNormPerDay() == null
                ? texts.get(language, BotTextKeys.PROFILE_FIELD_CALORIES_EMPTY)
                : texts.get(language, BotTextKeys.PROFILE_FIELD_CALORIES, String.valueOf(profile.getCaloriesNormPerDay()))
                + " " + texts.get(language, profile.isManualNorm()
                ? BotTextKeys.PROFILE_FIELD_MARK_MANUAL : BotTextKeys.PROFILE_FIELD_MARK_FORMULA));

        Integer formula = profileService.calcFormulaCaloriesOrNull(profile);
        if (formula != null && profile.isManualNorm()) {
            sb.append("\n").append(texts.get(language, BotTextKeys.PROFILE_FIELD_CALORIES_FORMULA, String.valueOf(formula)));
        }
        sb.append("\n\n").append(texts.get(language, BotTextKeys.PROFILE_FIELD_CALORIES_HINT));
        return sb.toString();
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.CALORIE_NORM;
    }
}