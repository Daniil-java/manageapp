package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportStatsCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Аналитика Insights: календарь на каждый день, точки веса только там, где есть тренд,
 * блоки без данных — null, а не пустые объекты.
 */
class InsightsAnalyticsDtoTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    private static final LocalDate END = START.plusDays(29);
    private static final UserNutritionProfile PROFILE = new UserNutritionProfile().setCaloriesNormPerDay(2000)
            .setCurrentWeightKg(new BigDecimal("80"));

    @Test
    void fullMonthHasAllBlocks() {
        List<Dish> dishes = new ArrayList<>();
        List<WeightEntry> weights = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            dishes.add(dish(START.plusDays(i), 9, 700));
            dishes.add(dish(START.plusDays(i), 21, 1300));
            if (i % 3 == 0) weights.add(new WeightEntry().setEntryDate(START.plusDays(i)).setWeight(BigDecimal.valueOf(80 - i * 0.05)));
        }

        InsightsAnalyticsDto dto = InsightsAnalyticsDto.of(ReportStatsCalculator.buildStats(source(dishes, weights)));

        assertThat(dto.calendar()).hasSize(30);
        assertThat(dto.calendar().get(0).status()).isEqualTo(ReportStats.DayStatus.OK);
        assertThat(dto.weight().points()).hasSize(30);
        assertThat(dto.weight().points().get(1).weight()).isNull();
        assertThat(dto.weight().forecastKg()).isNotNull();
        assertThat(dto.energy().realTdee()).isGreaterThan(2000);
        assertThat(dto.habits().firstMeal()).isEqualTo("09:00");
        assertThat(dto.habits().lastMeal()).isEqualTo("21:00");
        assertThat(dto.habits().lateCaloriesPercent()).isEqualTo(65);
        assertThat(dto.habits().proteinPerKg()).isNotNull();
    }

    @Test
    void blocksWithoutDataAreNull() {
        InsightsAnalyticsDto dto = InsightsAnalyticsDto.of(ReportStatsCalculator.buildStats(source(List.of(), List.of())));

        assertThat(dto.calendar()).hasSize(30).allMatch(d -> d.calories() == null);
        assertThat(dto.weight()).isNull();
        assertThat(dto.energy()).isNull();
        assertThat(dto.habits()).isNull();
    }

    private static ReportSource source(List<Dish> dishes, List<WeightEntry> weights) {
        return new ReportSource(ReportKind.MONTH, START, END, ZONE, dishes, List.of(), List.of(), weights, List.of(), PROFILE);
    }

    private static Dish dish(LocalDate date, int hour, int calories) {
        return new Dish().setName("Rice").setCalories(calories).setProteins(calories / 20).setFats(calories / 30)
                .setCarbohydrates(calories / 8).setCreated(date.atTime(hour, 0).atZone(ZONE).toInstant());
    }
}
