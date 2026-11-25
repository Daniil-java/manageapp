package com.kuklin.manageapp.bots.caloriebot.report;

import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.Micronutrient;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats.MicronutrientStat;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats.Status;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NutrientStatsCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 9);
    private static final LocalDate DAY_2 = DAY_1.plusDays(1);
    private static final LocalDate DAY_3 = DAY_1.plusDays(2);

    @Test
    void noFiberAndNoMicronutrientsMeansNoBlock() {
        ReportSource source = buildSource(female(), List.of(dish(DAY_1, null, null)));

        assertThat(NutrientStatsCalculator.buildOrNull(source)).isNull();
    }

    /** Среднее — по дням, где клетчатка есть; день старых записей без неё не занижает его. */
    @Test
    void fiberAverageSkipsDaysWithoutData() {
        ReportSource source = buildSource(female(), List.of(
                dish(DAY_1, 6, null), dish(DAY_1, 10, null),
                dish(DAY_2, 14, null),
                dish(DAY_3, null, null)));

        NutrientStats stats = NutrientStatsCalculator.buildOrNull(source);

        assertThat(stats.fiber().avgGrams()).isEqualTo(15);
        assertThat(stats.fiber().targetGrams()).isEqualTo(25);
        assertThat(stats.fiber().days()).isEqualTo(2);
        // 15 из 25 — меньше 70%
        assertThat(stats.fiber().status()).isEqualTo(Status.LOW);
        assertThat(stats.micronutrients()).isEmpty();
        assertThat(stats.loggedDays()).isEqualTo(3);
    }

    @Test
    void micronutrientStatusesFollowNormsAndLimits() {
        ReportSource source = buildSource(female(), List.of(
                dish(DAY_1, 5, Map.of("ironMg", 4.0, "sodiumMg", 1500.0, "vitaminCMg", 50.0)),
                dish(DAY_1, 5, Map.of("ironMg", 4.0, "sodiumMg", 1500.0, "vitaminCMg", 50.0)),
                dish(DAY_2, 5, null)));

        NutrientStats stats = NutrientStatsCalculator.buildOrNull(source);

        assertThat(stats.micronutrientDays()).isEqualTo(1);
        assertThat(stats.micronutrients()).hasSize(Micronutrient.values().length);
        // Женщина 34 лет: железо 18 мг — 8 мг это мало; натрий 3000 при пределе 2000 — перебор; витамин C 100 из 75 — норма
        assertThat(find(stats, Micronutrient.IRON).status()).isEqualTo(Status.LOW);
        assertThat(find(stats, Micronutrient.IRON).norm()).isEqualTo(18);
        assertThat(find(stats, Micronutrient.SODIUM).status()).isEqualTo(Status.HIGH);
        assertThat(find(stats, Micronutrient.VITAMIN_C).status()).isEqualTo(Status.OK);
        assertThat(find(stats, Micronutrient.VITAMIN_C).getPercent()).isEqualTo(133);
        // Не оценённое ИИ — ноль: у вещества с верхним пределом это «норма», у остальных — «мало»
        assertThat(find(stats, Micronutrient.ADDED_SUGAR).status()).isEqualTo(Status.OK);
        assertThat(find(stats, Micronutrient.CALCIUM).status()).isEqualTo(Status.LOW);
    }

    @Test
    void normsDependOnSexAgeAndCalories() {
        assertThat(Micronutrient.IRON.getDailyNorm(UserNutritionProfile.Sex.MALE, 30, 2000)).isEqualTo(8);
        assertThat(Micronutrient.IRON.getDailyNorm(UserNutritionProfile.Sex.FEMALE, 55, 2000)).isEqualTo(8);
        assertThat(Micronutrient.IRON.getDailyNorm(null, null, 2000)).isEqualTo(13);
        assertThat(Micronutrient.CALCIUM.getDailyNorm(UserNutritionProfile.Sex.FEMALE, 55, 2000)).isEqualTo(1200);
        assertThat(Micronutrient.SATURATED_FAT.getDailyNorm(null, null, 1800)).isCloseTo(20, within(0.01));
        assertThat(Micronutrient.ADDED_SUGAR.getDailyNorm(null, null, 2000)).isCloseTo(50, within(0.01));
        assertThat(NutrientStatsCalculator.getFiberTarget(UserNutritionProfile.Sex.MALE)).isEqualTo(30);
        assertThat(NutrientStatsCalculator.getFiberTarget(null)).isEqualTo(28);
    }

    /** Порция изменилась — клетчатка и микронутриенты за ней, как КБЖУ. */
    @Test
    void dishScalesFiberAndMicronutrientsWithPortion() {
        Dish dish = new Dish().setWeight(200).setPortions(1).setPortionWeight(200).setCalories(300).setFiber(4)
                .setMicronutrients(Map.of("ironMg", 2.0, "vitaminDMcg", 0.3));

        dish.applyPortionWeightPercentDelta(50);

        assertThat(dish.getWeight()).isEqualTo(300);
        assertThat(dish.getFiber()).isEqualTo(6);
        assertThat(dish.getMicronutrients()).containsEntry("ironMg", 3.0).containsEntry("vitaminDMcg", 0.45);
    }

    /** PATCH из мини-аппа с новым весом пересчитывает клетчатку, даже если её нет в запросе. */
    @Test
    void patchWithNewWeightScalesFiber() {
        Dish dish = new Dish().setWeight(100).setFiber(5).setMicronutrients(Map.of("calciumMg", 120.0));

        new com.kuklin.manageapp.bots.caloriebot.dish.dto.DishDto().setWeight(150).setCalories(200).mergeToEntity(dish);

        assertThat(dish.getFiber()).isEqualTo(8);
        assertThat(dish.getMicronutrients()).containsEntry("calciumMg", 180.0);
        assertThat(dish.getWeight()).isEqualTo(150);
    }

    /** Названия веществ и единицы — ключи строятся из enum, поэтому проверяем, что они есть в обоих словарях. */
    @Test
    void everyNutrientHasNameAndUnitInBothLanguages() throws java.io.IOException {
        for (String language : List.of("ru", "en")) {
            java.util.Properties pdf = new java.util.Properties();
            try (var in = getClass().getResourceAsStream("/i18n/calorie/pdf_" + language + ".properties")) {
                pdf.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
            for (Micronutrient nutrient : Micronutrient.values()) {
                assertThat(pdf.getProperty(nutrient.getTextKey())).as(language + " " + nutrient).isNotBlank();
                assertThat(pdf.getProperty(nutrient.getUnit().getTextKey())).as(language + " " + nutrient.getUnit()).isNotBlank();
            }
        }
    }

    private static MicronutrientStat find(NutrientStats stats, Micronutrient nutrient) {
        return stats.micronutrients().stream().filter(m -> m.nutrient() == nutrient).findFirst().orElseThrow();
    }

    private static UserNutritionProfile female() {
        return new UserNutritionProfile().setSex(UserNutritionProfile.Sex.FEMALE).setAgeYears(34).setCaloriesNormPerDay(1800);
    }

    private static Dish dish(LocalDate date, Integer fiber, Map<String, Double> micronutrients) {
        return new Dish().setCalories(400).setFiber(fiber).setMicronutrients(micronutrients)
                .setCreated(date.atTime(13, 0).atZone(ZONE).toInstant());
    }

    private static ReportSource buildSource(UserNutritionProfile profile, List<Dish> dishes) {
        return new ReportSource(ReportKind.WEEK, DAY_1, DAY_1.plusDays(6), ZONE, dishes, List.of(), List.of(), List.of(),
                List.of(), profile);
    }
}
