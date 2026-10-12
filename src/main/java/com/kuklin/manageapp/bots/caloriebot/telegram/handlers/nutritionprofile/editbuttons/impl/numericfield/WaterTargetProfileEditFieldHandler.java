package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.CalorieNutritionProfileRecalculate;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.ProfileEditAction;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class WaterTargetProfileEditFieldHandler
        extends AbstractNumericProfileEditFieldHandler {

    public WaterTargetProfileEditFieldHandler(
            UserNutritionProfileService profileService,
            CalorieNutritionProfileEditCallbackUpdateHandler callbackHandler
    ) {
        super(profileService, callbackHandler,
                2000,
                UserNutritionProfile.WATER_NORM_MIN,
                UserNutritionProfile.WATER_NORM_MAX,
                -500,
                -100,
                100,
                500
        );
    }

    @Override
    protected Integer getCurrentValue(UserNutritionProfile profile) {
        return profile.getWaterTargetMlPerDay();
    }

    @Override
    protected UserNutritionProfile applyValue(UserNutritionProfile profile, Integer value) {
        // Своя норма воды → ручной режим воды: вес и активность её больше не меняют (калории — отдельно)
        return profile
                .setWaterMode(UserNutritionProfile.NormMode.MANUAL)
                .setWaterTargetMlPerDay(value);
    }

    @Override
    protected String buildText(UserNutritionProfile profile) {
        AppLanguage language = texts.languageOf(profile.getUserId());
        StringBuilder sb = new StringBuilder(profile.getWaterTargetMlPerDay() == null
                ? texts.get(language, BotTextKeys.PROFILE_FIELD_WATER_EMPTY)
                : texts.get(language, BotTextKeys.PROFILE_FIELD_WATER, String.valueOf(profile.getWaterTargetMlPerDay()))
                + " " + texts.get(language, profile.isManualWater()
                ? BotTextKeys.PROFILE_FIELD_MARK_MANUAL : BotTextKeys.PROFILE_FIELD_MARK_FORMULA));

        Integer formula = UserNutritionProfileService.calcFormulaWaterOrNull(profile);
        if (formula != null && profile.isManualWater()) {
            sb.append("\n").append(texts.get(language, BotTextKeys.PROFILE_FIELD_WATER_FORMULA, String.valueOf(formula)));
        }
        sb.append("\n\n");
        if (profile.isManualWater()) {
            sb.append(texts.get(language, BotTextKeys.PROFILE_FIELD_WATER_HINT_MANUAL));
            if (formula != null) {
                sb.append(" ").append(texts.get(language, BotTextKeys.PROFILE_FIELD_WATER_HINT_MANUAL_FORMULA));
            }
        } else {
            sb.append(texts.get(language, BotTextKeys.PROFILE_FIELD_WATER_HINT_AUTO));
        }
        return sb.toString();
    }

    // Своя вода и есть из чего посчитать формулу — даём вернуться к ней
    @Override
    protected List<InlineKeyboardButton> extraRow(UserNutritionProfile profile) {
        if (!profile.isManualWater() || UserNutritionProfileService.calcFormulaWaterOrNull(profile) == null) {
            return List.of();
        }
        return List.of(TelegramKeyboard.button(
                texts.get(profile.getUserId(), BotTextKeys.PROFILE_FIELD_BUTTON_WATER_FORMULA),
                CalorieNutritionProfileRecalculate.callback(CalorieNutritionProfileRecalculate.WATER_AUTO)));
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.WATER_TARGET;
    }
}
