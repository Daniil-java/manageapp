package com.kuklin.manageapp.bots.caloriebot.dish;

import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Витамины, минералы и подробности по жирам и сахару — оценивает ИИ при распознавании, только для Premium.
 * В блюде хранятся в {@link Dish#getMicronutrients()}: ключ — {@link #getKey()}, значение — на всю порцию.
 * Ключ не менять: он лежит в jsonb уже записанных блюд и в схеме ответа ИИ. Единица — в самом ключе,
 * так ИИ не путает миллиграммы с микрограммами.
 * <p>
 * Нормы — ориентиры для взрослых (ВОЗ, EFSA, NIH), оценка по фото примерная: в отчёте важен вывод «мало / норма / перебор».
 */
@Getter
@RequiredArgsConstructor
public enum Micronutrient {
    SATURATED_FAT("saturatedFatG", Unit.G, Limit.MAX),
    ADDED_SUGAR("addedSugarG", Unit.G, Limit.MAX),
    OMEGA_3("omega3G", Unit.G, Limit.MIN),
    PUFA("pufaG", Unit.G, Limit.MIN),
    SODIUM("sodiumMg", Unit.MG, Limit.MAX),
    POTASSIUM("potassiumMg", Unit.MG, Limit.MIN),
    CALCIUM("calciumMg", Unit.MG, Limit.MIN),
    IRON("ironMg", Unit.MG, Limit.MIN),
    MAGNESIUM("magnesiumMg", Unit.MG, Limit.MIN),
    VITAMIN_C("vitaminCMg", Unit.MG, Limit.MIN),
    VITAMIN_D("vitaminDMcg", Unit.MCG, Limit.MIN),
    VITAMIN_B12("vitaminB12Mcg", Unit.MCG, Limit.MIN);

    /** Ключ в jsonb блюда и в схеме ответа ИИ (на 100 г — там же, где КБЖУ на 100 г) */
    private final String key;
    private final Unit unit;
    /** MIN — норма, которую стоит набрать; MAX — верхний предел, больше — перебор */
    private final Limit limit;

    public enum Unit {
        G, MG, MCG;

        /** Подпись единицы — ключ словаря pdf_* */
        public String getTextKey() {
            return "pdf.unit." + name().toLowerCase();
        }
    }

    public enum Limit {MIN, MAX}

    /** Название на языке пользователя — ключ словаря pdf_* */
    public String getTextKey() {
        return "pdf.nutrient." + key;
    }

    /**
     * Норма в день для этого человека. Пол или возраст неизвестны — среднее между мужской и женской нормой
     * и норма для 19–50 лет.
     *
     * @param caloriesTarget норма калорий в день — от неё считаются насыщенные жиры, сахар и ПНЖК (доля калорий)
     */
    public double getDailyNorm(UserNutritionProfile.Sex sex, Integer age, int caloriesTarget) {
        int years = age != null ? age : 30;
        return switch (this) {
            // ВОЗ: насыщенные и добавленный сахар — не больше 10% калорий
            case SATURATED_FAT -> caloriesTarget * 0.10 / 9;
            case ADDED_SUGAR -> caloriesTarget * 0.10 / 4;
            case OMEGA_3 -> bySex(sex, 1.6, 1.1);
            // ПНЖК: 6–10% калорий, ориентир — нижняя граница
            case PUFA -> caloriesTarget * 0.06 / 9;
            case SODIUM -> 2000;
            case POTASSIUM -> 3500;
            case CALCIUM -> (sex == UserNutritionProfile.Sex.FEMALE && years > 50)
                    || (sex == UserNutritionProfile.Sex.MALE && years > 70) ? 1200 : 1000;
            case IRON -> bySex(sex, 8, years > 50 ? 8 : 18);
            case MAGNESIUM -> bySex(sex, 420, 320);
            case VITAMIN_C -> bySex(sex, 90, 75);
            case VITAMIN_D -> years > 70 ? 20 : 15;
            case VITAMIN_B12 -> 2.4;
        };
    }

    private static double bySex(UserNutritionProfile.Sex sex, double male, double female) {
        if (sex == UserNutritionProfile.Sex.MALE) return male;
        if (sex == UserNutritionProfile.Sex.FEMALE) return female;
        return (male + female) / 2;
    }

    /**
     * Значения на 100 г от ИИ → на всю порцию. Отрицательные и пустые отбрасываются, незнакомые ключи — тоже.
     * null — ИИ микронутриенты не оценивал.
     */
    public static Map<String, Double> buildTotalsOrNull(Map<String, Double> per100g, int weightGrams) {
        if (per100g == null || per100g.isEmpty()) return null;
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Micronutrient nutrient : values()) {
            Double value = per100g.get(nutrient.key);
            if (value != null && value >= 0) totals.put(nutrient.key, round(value * weightGrams / 100));
        }
        return totals.isEmpty() ? null : totals;
    }

    /** Те же значения, умноженные на multiplier (порция изменилась). null — null. */
    public static Map<String, Double> scaleOrNull(Map<String, Double> totals, double multiplier) {
        if (totals == null) return null;
        double factor = Math.max(0, multiplier);
        Map<String, Double> scaled = new LinkedHashMap<>();
        totals.forEach((key, value) -> scaled.put(key, value == null ? null : round(value * factor)));
        return scaled;
    }

    /** Два знака: витамины D и B12 — доли микрограмма */
    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
