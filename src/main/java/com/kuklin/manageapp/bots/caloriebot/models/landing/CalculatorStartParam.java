package com.kuklin.manageapp.bots.caloriebot.models.landing;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.ActivityLevel;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Goal;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile.Sex;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Параметр /start из калькулятора лендинга: p_F_28_168_65_M_L[_<utm-код>].
 * Пол, возраст, рост, вес, активность (L/M/H), цель (L — похудеть, M — держать, G — набрать).
 * <p>
 * Telegram пропускает в start до 64 символов [A-Za-z0-9_-], поэтому коды однобуквенные.
 * UTM-код лендинга идёт в хвосте и сам может содержать «_» — всё после седьмой части считается им.
 */
public record CalculatorStartParam(Calculator calculator, String utmCode) {

    public static final String PREFIX = "p";
    private static final String SEPARATOR = "_";
    private static final int CALCULATOR_PARTS = 7;

    private static final Map<String, Sex> SEX = Map.of("F", Sex.FEMALE, "M", Sex.MALE);
    private static final Map<String, ActivityLevel> ACTIVITY =
            Map.of("L", ActivityLevel.LOW, "M", ActivityLevel.MEDIUM, "H", ActivityLevel.HIGH);
    private static final Map<String, Goal> GOAL =
            Map.of("L", Goal.LOSE_WEIGHT, "M", Goal.MAINTAIN, "G", Goal.GAIN_WEIGHT);

    /** Данные калькулятора, уже проверенные на границы профиля. */
    public record Calculator(Sex sex, int ageYears, int heightCm, int weightKg,
                             ActivityLevel activityLevel, Goal goal) {

        public boolean sameAs(UserNutritionProfile profile) {
            return sex == profile.getSex()
                    && Integer.valueOf(ageYears).equals(profile.getAgeYears())
                    && Integer.valueOf(heightCm).equals(profile.getHeightCm())
                    && profile.getCurrentWeightKg() != null
                    && BigDecimal.valueOf(weightKg).compareTo(profile.getCurrentWeightKg()) == 0
                    && activityLevel == profile.getActivityLevel()
                    && goal == profile.getGoal();
        }

        /** Без UTM — для callback кнопки «Обновить профиль». */
        public String encode() {
            return String.join(SEPARATOR, PREFIX,
                    key(SEX, sex), String.valueOf(ageYears), String.valueOf(heightCm), String.valueOf(weightKg),
                    key(ACTIVITY, activityLevel), key(GOAL, goal));
        }

        private static <T> String key(Map<String, T> codes, T value) {
            return codes.entrySet().stream()
                    .filter(e -> e.getValue() == value)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * Разобрать параметр /start. Не калькулятор — весь параметр считается UTM-кодом, как раньше.
     * Битые данные калькулятора отбрасываются, UTM из хвоста всё равно засчитывается.
     */
    public static CalculatorStartParam parse(String param) {
        if (param == null || param.isBlank()) {
            return new CalculatorStartParam(null, null);
        }
        if (!param.startsWith(PREFIX + SEPARATOR)) {
            return new CalculatorStartParam(null, param);
        }
        String[] parts = param.split(SEPARATOR, CALCULATOR_PARTS + 1);
        String utm = parts.length > CALCULATOR_PARTS && !parts[CALCULATOR_PARTS].isBlank()
                ? parts[CALCULATOR_PARTS]
                : null;
        return new CalculatorStartParam(parts.length < CALCULATOR_PARTS ? null : parseCalculator(parts), utm);
    }

    private static Calculator parseCalculator(String[] parts) {
        Sex sex = SEX.get(parts[1]);
        Integer age = intInRange(parts[2], UserNutritionProfile.AGE_MIN, UserNutritionProfile.AGE_MAX);
        Integer height = intInRange(parts[3], UserNutritionProfile.HEIGHT_MIN, UserNutritionProfile.HEIGHT_MAX);
        Integer weight = intInRange(parts[4], UserNutritionProfile.WEIGHT_MIN, UserNutritionProfile.WEIGHT_MAX);
        ActivityLevel activity = ACTIVITY.get(parts[5]);
        Goal goal = GOAL.get(parts[6]);

        if (sex == null || age == null || height == null || weight == null || activity == null || goal == null) {
            return null;
        }
        return new Calculator(sex, age, height, weight, activity, goal);
    }

    private static Integer intInRange(String value, int min, int max) {
        if (value.isEmpty() || value.length() > 3 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        int number = Integer.parseInt(value);
        return number >= min && number <= max ? number : null;
    }
}
