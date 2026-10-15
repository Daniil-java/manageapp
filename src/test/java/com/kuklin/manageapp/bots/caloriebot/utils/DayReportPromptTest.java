package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DayReportPromptTest {

    private static final ZonedDateTime USER_TIME =
            ZonedDateTime.of(2026, 10, 14, 21, 5, 0, 0, ZoneId.of("Europe/Moscow"));

    private static final UserNutritionProfile PROFILE = new UserNutritionProfile()
            .setUserId(42L)
            .setSex(UserNutritionProfile.Sex.FEMALE)
            .setCurrentWeightKg(new BigDecimal("64.50"))
            .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT)
            .setCaloriesNormPerDay(1800)
            .setProteinsNormGramsPerDay(100);

    private static final List<Dish> DISHES = List.of(
            dish("Овсянка", "2026-10-14T05:30:00Z", 350, 12),
            dish("Пицца", "2026-10-14T16:00:00Z", 1600, 50)
    );

    @Test
    void totalsAreCountedByCode() {
        assertThat(DayReportPrompt.buildTotalsText(PROFILE, DISHES, AppLanguage.RU))
                .contains("калории 1950 из 1800 ккал, перебор 150")
                .contains("белки 62 из 100 г, осталось 38")
                .contains("жиры 0 г, норма не задана");
    }

    @Test
    void dishTimeIsLocal() {
        // 05:30 UTC → 08:30 МСК
        assertThat(DayReportPrompt.buildDishesText(DISHES, USER_TIME, AppLanguage.RU))
                .startsWith("08:30 — Овсянка, 200 г: 350 ккал, Б 12 / Ж 0 / У 0")
                .contains("19:00 — Пицца");
    }

    @Test
    void profileHasNoServiceFields() {
        assertThat(DayReportPrompt.buildProfileText(PROFILE, AppLanguage.RU))
                .isEqualTo("пол FEMALE, вес 64.5 кг, цель похудение")
                .doesNotContain("42");
    }

    @Test
    void promptHasWeekdayIntroAndTipTheme() {
        String prompt = DayReportPrompt.buildPrompt(PROFILE, DISHES, USER_TIME, AppLanguage.RU, new Random(1));

        assertThat(prompt)
                .contains("Сейчас у пользователя: среда, 14 октября 2026, 21:05")
                .contains("ЯЗЫК ОТВЕТА: Русский.")
                .doesNotContain("$s");
        assertThat(DayReportPrompt.RU.introFormats()).anyMatch(prompt::contains);
        assertThat(DayReportPrompt.getTipThemes(UserNutritionProfile.Goal.LOSE_WEIGHT, AppLanguage.RU)).anyMatch(prompt::contains);
    }

    @Test
    void introFormatRotates() {
        Random random = new Random(7);
        Set<String> intros = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            String prompt = DayReportPrompt.buildPrompt(PROFILE, DISHES, USER_TIME, AppLanguage.RU, random);
            DayReportPrompt.RU.introFormats().stream().filter(prompt::contains).forEach(intros::add);
        }
        assertThat(intros).hasSizeGreaterThan(5);
    }

    @Test
    void gainGoalGetsGainRulesAndThemes() {
        UserNutritionProfile gaining = PROFILE.copy()
                .setGoal(UserNutritionProfile.Goal.GAIN_WEIGHT)
                .setDietType(UserNutritionProfile.DietType.VEGAN);

        String prompt = DayReportPrompt.buildPrompt(gaining, DISHES, USER_TIME, AppLanguage.RU, new Random(1));

        assertThat(prompt)
                .contains("цель набор веса, питание веганское")
                .contains("Цель: набор веса.")
                .contains("Главный риск — недобор калорий")
                .contains("Тип питания: веганское.")
                .doesNotContain("$s");
        assertThat(DayReportPrompt.getTipThemes(UserNutritionProfile.Goal.GAIN_WEIGHT, AppLanguage.RU)).anyMatch(prompt::contains);
    }

    @Test
    void tipThemesFitGoal() {
        assertThat(DayReportPrompt.getTipThemes(UserNutritionProfile.Goal.GAIN_WEIGHT, AppLanguage.RU))
                .doesNotContain("есть медленнее и без экрана", "специи и вкус без лишних калорий")
                .contains("больше приёмов пищи вместо огромных порций");
        assertThat(DayReportPrompt.getTipThemes(UserNutritionProfile.Goal.LOSE_WEIGHT, AppLanguage.RU))
                .contains("есть медленнее и без экрана")
                .doesNotContain("больше приёмов пищи вместо огромных порций");
        // Цель не указана — темы как для поддержания
        assertThat(DayReportPrompt.getTipThemes(null, AppLanguage.RU))
                .isEqualTo(DayReportPrompt.getTipThemes(UserNutritionProfile.Goal.MAINTAIN, AppLanguage.RU));
    }

    private static Dish dish(String name, String createdUtc, int calories, int proteins) {
        return new Dish()
                .setName(name)
                .setCalories(calories)
                .setProteins(proteins)
                .setWeight(200)
                .setCreated(Instant.parse(createdUtc));
    }
}
