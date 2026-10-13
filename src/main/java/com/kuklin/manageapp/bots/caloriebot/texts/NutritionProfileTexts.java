package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import org.springframework.stereotype.Component;

/**
 * Профиль питания текстом на языке пользователя (раньше — UserNutritionProfile.toTelegramView).
 * Язык берём у владельца профиля (profile.userId).
 */
@Component
public class NutritionProfileTexts extends AbstractBotTexts {

    public NutritionProfileTexts(CalorieTexts texts) {
        super(texts);
    }

    /** Подпись значения (пол, активность, цель, диета) на языке пользователя. */
    public String getLabel(UserNutritionProfile.Labeled value, AppLanguage language) {
        return texts.get(language, value.getTextKey());
    }

    // Числа передаём строками: ICU иначе поставил бы разделители разрядов (2 000 мл)
    public String buildProfileText(UserNutritionProfile profile) {
        AppLanguage language = languageOf(profile.getUserId());
        String noData = texts.get(language, BotTextKeys.PROFILE_VIEW_NO_DATA);
        StringBuilder sb = new StringBuilder();

        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_TITLE)).append("\n");
        sb.append("──────────────────────\n\n");

        // Основные данные
        String sex = profile.getSex() == null ? noData : texts.get(language, "profile.view.sex." + profile.getSex().name());
        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_SEX, sex)).append("\n");

        // Возраст — числом: от него зависит «год / года / лет»
        String age = profile.getAgeYears() == null ? noData
                : texts.get(language, BotTextKeys.PROFILE_VIEW_AGE_VALUE, profile.getAgeYears());
        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_AGE, age)).append("\n");

        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_HEIGHT,
                getWithUnit(profile.getHeightCm(), BotTextKeys.COMMON_UNIT_CM, language, noData))).append("\n");

        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_WEIGHT,
                getWithUnit(profile.getCurrentWeightKg(), BotTextKeys.COMMON_UNIT_KG, language, noData))).append("\n");

        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_ACTIVITY,
                labelOrNoData(profile.getActivityLevel(), language, noData))).append("\n");
        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_GOAL,
                labelOrNoData(profile.getGoal(), language, noData))).append("\n");
        sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_DIET,
                labelOrNoData(profile.getDietType(), language, noData))).append("\n");

        // Нормы по калориям и БЖУ
        sb.append("\n").append(texts.get(language, BotTextKeys.PROFILE_VIEW_NORMS_TITLE)).append("\n");

        boolean hasTargets =
                profile.getCaloriesNormPerDay() != null ||
                        profile.getProteinsNormGramsPerDay() != null ||
                        profile.getFatsNormGramsPerDay() != null ||
                        profile.getCarbsNormGramsPerDay() != null;

        if (!hasTargets) {
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_NORMS_NO_DATA)).append("\n");
        } else {
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_CALORIES,
                            getWithUnit(profile.getCaloriesNormPerDay(), BotTextKeys.COMMON_UNIT_KCAL, language, noData)))
                    .append(profile.isManualNorm() ? " " + texts.get(language, BotTextKeys.PROFILE_VIEW_MANUAL_MARK) : "")
                    .append("\n");
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_PROTEINS,
                    getWithUnit(profile.getProteinsNormGramsPerDay(), BotTextKeys.COMMON_UNIT_GRAM, language, noData))).append("\n");
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_FATS,
                    getWithUnit(profile.getFatsNormGramsPerDay(), BotTextKeys.COMMON_UNIT_GRAM, language, noData))).append("\n");
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_CARBS,
                    getWithUnit(profile.getCarbsNormGramsPerDay(), BotTextKeys.COMMON_UNIT_GRAM, language, noData))).append("\n");
            sb.append(texts.get(language, profile.isManualNorm()
                    ? BotTextKeys.PROFILE_VIEW_MODE_MANUAL
                    : BotTextKeys.PROFILE_VIEW_MODE_AUTO)).append("\n");
        }

        // Вода
        sb.append("\n").append(texts.get(language, BotTextKeys.PROFILE_VIEW_WATER_TITLE)).append("\n");
        Integer water = profile.getWaterTargetMlPerDay();
        if (water == null) {
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_WATER_NO_DATA)).append("\n");
        } else {
            sb.append(texts.get(language, BotTextKeys.PROFILE_VIEW_WATER,
                            String.valueOf(water), String.format("%.1f", water / 1000.0)))
                    .append(profile.isManualWater() ? " " + texts.get(language, BotTextKeys.PROFILE_VIEW_MANUAL_MARK) : "")
                    .append("\n");
        }

        return sb.toString();
    }

    private String labelOrNoData(UserNutritionProfile.Labeled value, AppLanguage language, String noData) {
        return value == null ? noData : getLabel(value, language);
    }
}
