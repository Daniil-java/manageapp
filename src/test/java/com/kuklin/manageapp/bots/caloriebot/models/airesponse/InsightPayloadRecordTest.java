package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InsightPayloadRecordTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow"); // UTC+3

    @Test
    void mealTimeIsConvertedToUserZoneAndTruncatedToMinutes() {
        Dish dish = new Dish().setName("Soup").setCreated(Instant.parse("2026-10-01T21:15:42.123Z"));

        InsightPayloadRecord payload = InsightPayloadRecord.of(profile(), List.of(dish), List.of(), ZONE);

        assertThat(payload.dishes()).singleElement()
                .extracting(InsightPayloadRecord.InsightDish::eatenAt)
                .isEqualTo(LocalDateTime.of(2026, 10, 2, 0, 15));
    }

    @Test
    void dishesAreSortedByTime() {
        Dish late = new Dish().setName("Dinner").setCreated(Instant.parse("2026-10-01T17:00:00Z"));
        Dish early = new Dish().setName("Breakfast").setCreated(Instant.parse("2026-10-01T05:00:00Z"));

        InsightPayloadRecord payload = InsightPayloadRecord.of(profile(), List.of(late, early), List.of(), ZONE);

        assertThat(payload.dishes()).extracting(InsightPayloadRecord.InsightDish::name)
                .containsExactly("Breakfast", "Dinner");
    }

    @Test
    void dishWithoutTimeIsKeptWithNullTime() {
        Dish noTime = new Dish().setName("Snack");

        InsightPayloadRecord payload = InsightPayloadRecord.of(profile(), List.of(noTime), List.of(), ZONE);

        assertThat(payload.dishes()).singleElement()
                .satisfies(d -> {
                    assertThat(d.name()).isEqualTo("Snack");
                    assertThat(d.eatenAt()).isNull();
                });
    }

    @Test
    void keepsTwentyLatestWeightsInChronologicalOrder() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        List<WeightEntry> weights = new ArrayList<>(IntStream.range(0, 25)
                .mapToObj(i -> new WeightEntry()
                        .setEntryDate(start.plusDays(i))
                        .setWeight(BigDecimal.valueOf(80 + i * 0.1)))
                .toList());
        Collections.shuffle(weights);

        InsightPayloadRecord payload = InsightPayloadRecord.of(profile(), List.of(), weights, ZONE);

        assertThat(payload.weights()).hasSize(20);
        assertThat(payload.weights().get(0).date()).isEqualTo(start.plusDays(5));
        assertThat(payload.weights().get(19).date()).isEqualTo(start.plusDays(24));
        assertThat(payload.weights()).isSortedAccordingTo(
                (a, b) -> a.date().compareTo(b.date()));
    }

    @Test
    void skipsWeightsWithoutDateOrValue() {
        List<WeightEntry> weights = List.of(
                new WeightEntry().setEntryDate(LocalDate.of(2026, 10, 1)).setWeight(BigDecimal.valueOf(80)),
                new WeightEntry().setEntryDate(null).setWeight(BigDecimal.valueOf(81)),
                new WeightEntry().setEntryDate(LocalDate.of(2026, 10, 2)).setWeight(null)
        );

        InsightPayloadRecord payload = InsightPayloadRecord.of(profile(), List.of(), weights, ZONE);

        assertThat(payload.weights()).singleElement()
                .extracting(InsightPayloadRecord.InsightWeight::weightKg)
                .isEqualTo(BigDecimal.valueOf(80));
    }

    @Test
    void profileKeepsOnlyGoalsAndBodyData() {
        UserNutritionProfile profile = profile()
                .setHeightCm(180)
                .setCurrentWeightKg(BigDecimal.valueOf(82.5))
                .setProteinsNormGramsPerDay(120);

        InsightPayloadRecord.InsightProfile p = InsightPayloadRecord.of(profile, List.of(), List.of(), ZONE).profile();

        assertThat(p.goal()).isEqualTo(UserNutritionProfile.Goal.GAIN_WEIGHT);
        assertThat(p.heightCm()).isEqualTo(180);
        assertThat(p.currentWeightKg()).isEqualByComparingTo("82.5");
        assertThat(p.caloriesNormPerDay()).isEqualTo(2600);
        assertThat(p.proteinsNormGramsPerDay()).isEqualTo(120);
    }

    private static UserNutritionProfile profile() {
        return new UserNutritionProfile()
                .setId(5L)
                .setUserId(42L)
                .setGoal(UserNutritionProfile.Goal.GAIN_WEIGHT)
                .setCaloriesNormPerDay(2600);
    }
}
