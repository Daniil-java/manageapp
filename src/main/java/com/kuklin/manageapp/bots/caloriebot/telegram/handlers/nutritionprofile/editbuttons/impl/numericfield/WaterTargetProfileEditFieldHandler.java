package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons.impl.numericfield;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
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
        StringBuilder sb = new StringBuilder(profile.getWaterTargetMlPerDay() == null
                ? "Вода: не указана"
                : "Вода: " + profile.getWaterTargetMlPerDay() + " мл"
                + (profile.isManualWater() ? " (вручную)" : " (по формуле)"));

        Integer formula = UserNutritionProfileService.calcFormulaWaterOrNull(profile);
        if (formula != null && profile.isManualWater()) {
            sb.append("\nПо формуле: ").append(formula).append(" мл");
        }
        sb.append(profile.isManualWater()
                ? "\n\nСвоя норма не меняется от веса и активности."
                + (formula != null ? " Вернуть расчёт по весу — кнопка «↺ По формуле»." : "")
                : "\n\nПо формуле: вес × 30–35 мл в зависимости от активности. "
                + "Если сохранить своё число, оно перестанет меняться от веса и активности.");
        return sb.toString();
    }

    // Своя вода и есть из чего посчитать формулу — даём вернуться к ней
    @Override
    protected List<InlineKeyboardButton> extraRow(UserNutritionProfile profile) {
        if (!profile.isManualWater() || UserNutritionProfileService.calcFormulaWaterOrNull(profile) == null) {
            return List.of();
        }
        return List.of(TelegramKeyboard.button(
                "↺ По формуле",
                CalorieNutritionProfileRecalculate.callback(CalorieNutritionProfileRecalculate.WATER_AUTO)));
    }

    @Override
    public ProfileEditAction getProfileAction() {
        return ProfileEditAction.WATER_TARGET;
    }
}
