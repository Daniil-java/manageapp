package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportSource;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.DayStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Метрики PDF-отчёта: коридор нормы, тренд веса и реальный расход, выходные, серия, ритм и топы.
 */
class ReportStatsCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Понедельник: 30 дней — ровно четыре полных недели и два дня
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    private static final LocalDate END = START.plusDays(29);

    @Test
    void dayStatusByCorridor() {
        assertThat(ReportStatsCalculator.buildStatus(null, 2000)).isEqualTo(DayStatus.NONE);
        assertThat(ReportStatsCalculator.buildStatus(1790, 2000)).isEqualTo(DayStatus.UNDER);
        assertThat(ReportStatsCalculator.buildStatus(2200, 2000)).isEqualTo(DayStatus.OK);
        assertThat(ReportStatsCalculator.buildStatus(2500, 2000)).isEqualTo(DayStatus.OVER);
        assertThat(ReportStatsCalculator.buildStatus(2501, 2000)).isEqualTo(DayStatus.BIG_OVER);
        // Нормы нет — день просто записан
        assertThat(ReportStatsCalculator.buildStatus(3000, null)).isEqualTo(DayStatus.OK);
    }

    @Test
    void realExpenditureFromIntakeAndWeightTrend() {
        // 2 000 ккал каждый день, вес ровно −0,5 кг в неделю → расход 2 000 + 0,5 / 7 × 7 700 = 2 550
        List<Dish> dishes = new ArrayList<>();
        List<WeightEntry> weights = new ArrayList<>();
        // Взвешивания и до периода: сглаженный тренд к началу периода уже «разогнан»
        for (int i = -30; i < 30; i++) {
            if (i >= 0) dishes.add(dish(START.plusDays(i), 12, 2000, "Rice"));
            weights.add(weight(START.plusDays(i), 80 - 0.5 / 7 * i));
        }

        ReportStats stats = ReportStatsCalculator.buildStats(source(dishes, List.of(), weights, List.of()));

        assertThat(stats.weight().ratePerWeek()).isCloseTo(-0.5, within(0.05));
        assertThat(stats.realTdee()).isBetween(2500, 2600);
        assertThat(stats.weight().forecastKg()).isCloseTo(stats.weight().trendEnd() - 1.0, within(0.1));
        assertThat(stats.weight().measurements()).isEqualTo(30);
    }

    @Test
    void trendFollowsWeighInsWithoutLag() {
        // Вес ровно падает через день, с колебаниями воды ±0,3 — тренд идёт по середине, без отставания к концу
        List<WeightEntry> weights = new ArrayList<>();
        for (int i = 0; i < 30; i += 2) {
            weights.add(weight(START.plusDays(i), 80 - 0.1 * i + (i % 4 == 0 ? 0.3 : -0.3)));
        }

        ReportStats stats = ReportStatsCalculator.buildStats(source(List.of(), List.of(), weights, List.of()));

        assertThat(stats.days().get(14).trendWeight()).isCloseTo(80 - 1.4, within(0.15));
        assertThat(stats.weight().trendEnd()).isCloseTo(80 - 2.9, within(0.25));
        assertThat(stats.weight().ratePerWeek()).isCloseTo(-0.7, within(0.05));
    }

    @Test
    void noRealExpenditureWithoutEnoughWeighIns() {
        List<Dish> dishes = new ArrayList<>();
        for (int i = 0; i < 30; i++) dishes.add(dish(START.plusDays(i), 12, 2000, "Rice"));

        ReportStats stats = ReportStatsCalculator.buildStats(source(dishes, List.of(), List.of(weight(START, 80)), List.of()));

        assertThat(stats.weight()).isNull();
        assertThat(stats.realTdee()).isNull();
    }

    @Test
    void weekendsAndCorridorAndStreak() {
        List<Dish> dishes = new ArrayList<>();
        for (int i = 0; i < 29; i++) { // сегодняшний (последний) день пустой — серию он не обрывает
            LocalDate date = START.plusDays(i);
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (i == 10) continue; // пропуск
            dishes.add(dish(date, 12, weekend ? 2600 : 1800, "Rice"));
        }

        ReportStats stats = ReportStatsCalculator.buildStats(source(dishes, List.of(), List.of(), List.of()));

        assertThat(stats.loggedDays()).isEqualTo(28);
        assertThat(stats.weekdayAvgCalories()).isEqualTo(1800);
        assertThat(stats.weekendAvgCalories()).isEqualTo(2600);
        // Норма 2 000: 1 800 — ровно −10%, в коридоре; 2 600 — перебор 30%
        assertThat(stats.inCorridorDays()).isEqualTo(20);
        assertThat(stats.days().get(5).status()).isEqualTo(DayStatus.BIG_OVER);
        assertThat(stats.streakDays()).isEqualTo(18);
        // Без веса опора — норма: 2 × 800 лишних / 5 × 200 дефицита = 1.6
        assertThat(stats.weekendShare()).isCloseTo(1600.0 / 1000, within(0.01));
    }

    @Test
    void targetHistoryAppliesPerDay() {
        LocalDate change = START.plusDays(17);
        List<UserNutritionProfileEntry> targets = List.of(
                target(1850, START.minusDays(30), change),
                target(1750, change, null));
        List<Dish> dishes = List.of(dish(START, 12, 1850, "Rice"), dish(END, 12, 1750, "Rice"));

        ReportStats stats = ReportStatsCalculator.buildStats(source(dishes, targets, List.of(), List.of()));

        assertThat(stats.days().get(0).target()).isEqualTo(1850);
        assertThat(stats.days().get(29).target()).isEqualTo(1750);
        assertThat(stats.targetChange().date()).isEqualTo(change);
        assertThat(stats.inCorridorDays()).isEqualTo(2);
    }

    @Test
    void rhythmTopsAndWater() {
        List<Dish> dishes = List.of(
                dish(START, 9, 400, "Oatmeal"),
                dishAt(START, 9, 20, 100, "coffee "),     // через 20 минут — тот же приём пищи
                dish(START, 13, 700, "Pizza"),
                dish(START, 22, 300, "Chocolate"),
                dish(START.plusDays(1), 8, 400, "oatmeal"),
                dish(START.plusDays(1), 20, 600, "Pizza"));
        List<WaterEntryDto> water = List.of(
                new WaterEntryDto().setEntryDate(START).setAmount(1500),
                new WaterEntryDto().setEntryDate(START).setAmount(600),
                new WaterEntryDto().setEntryDate(START.plusDays(1)).setAmount(1000));

        ReportStats stats = ReportStatsCalculator.buildStats(source(dishes, List.of(), List.of(), water));

        assertThat(stats.rhythm().firstMealMinutes()).isEqualTo((9 * 60 + 8 * 60) / 2);
        assertThat(stats.rhythm().lastMealMinutes()).isEqualTo((22 * 60 + 20 * 60) / 2);
        assertThat(stats.rhythm().mealsPerDay()).isEqualTo(2.5);
        assertThat(stats.rhythm().lateShare()).isCloseTo(300.0 / 2500, within(0.001));
        // Одинаковая частота — выше то, что калорийнее; регистр названия не важен
        assertThat(stats.topByCount().get(0).name()).isEqualTo("Pizza");
        assertThat(stats.topByCount().get(1).name()).isEqualTo("oatmeal");
        assertThat(stats.topByCount().get(1).count()).isEqualTo(2);
        assertThat(stats.topByCalories().get(0).name()).isEqualTo("Pizza");
        assertThat(stats.topByCalories().get(0).calories()).isEqualTo(1300);
        assertThat(stats.water().avgMl()).isEqualTo(1550);
        assertThat(stats.water().daysMet()).isEqualTo(1);
    }

    @Test
    void previousPeriodNeedsThreeDays() {
        List<Dish> current = List.of(dish(START, 12, 2000, "Rice"));
        List<Dish> previous = List.of(dish(START.minusDays(3), 12, 2400, "Rice"), dish(START.minusDays(2), 12, 2200, "Rice"),
                dish(START.minusDays(1), 12, 2200, "Rice"));

        ReportSource source = new ReportSource(ReportKind.MONTH, START, END, ZONE, current, previous, List.of(), List.of(), List.of(), profile());

        assertThat(ReportStatsCalculator.buildStats(source).prevAvgCalories()).isEqualTo(2267);
        assertThat(ReportStatsCalculator.buildStats(new ReportSource(ReportKind.MONTH, START, END, ZONE, current,
                previous.subList(0, 2), List.of(), List.of(), List.of(), profile())).prevAvgCalories()).isNull();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static ReportSource source(List<Dish> dishes, List<UserNutritionProfileEntry> targets,
                                       List<WeightEntry> weights, List<WaterEntryDto> water) {
        return new ReportSource(ReportKind.MONTH, START, END, ZONE, dishes, List.of(), targets, weights, water, profile());
    }

    private static UserNutritionProfile profile() {
        return new UserNutritionProfile().setCaloriesNormPerDay(2000).setWaterTargetMlPerDay(2000);
    }

    private static Dish dish(LocalDate date, int hour, int calories, String name) {
        return dishAt(date, hour, 0, calories, name);
    }

    private static Dish dishAt(LocalDate date, int hour, int minute, int calories, String name) {
        return new Dish()
                .setName(name)
                .setCalories(calories)
                .setProteins(calories / 20)
                .setFats(calories / 30)
                .setCarbohydrates(calories / 8)
                .setCreated(date.atTime(hour, minute).atZone(ZONE).toInstant());
    }

    private static WeightEntry weight(LocalDate date, double kg) {
        return new WeightEntry().setEntryDate(date).setWeight(BigDecimal.valueOf(kg));
    }

    private static UserNutritionProfileEntry target(int calories, LocalDate from, LocalDate to) {
        return new UserNutritionProfileEntry()
                .setCaloriesNormPerDay(calories)
                .setValidFrom(from.atStartOfDay(ZONE).toInstant())
                .setValidTo(to != null ? to.atStartOfDay(ZONE).toInstant() : null);
    }
}
