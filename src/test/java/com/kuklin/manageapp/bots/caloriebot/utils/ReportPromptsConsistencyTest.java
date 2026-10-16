package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Промпты отчётов есть на русском и английском. Правишь один — правь и второй: тест ловит выпавшую подстановку,
 * разное число вступлений и тем совета и русский текст, оставшийся в английском промпте.
 */
class ReportPromptsConsistencyTest {

    private static final ZonedDateTime USER_TIME =
            ZonedDateTime.of(2026, 10, 14, 21, 5, 0, 0, ZoneId.of("Europe/Moscow"));

    @Test
    void ruAndEnTemplatesHaveSamePlaceholders() {
        assertThat(findPlaceholders(ReportPrompts.EN.todayAnalysis()))
                .isEqualTo(findPlaceholders(ReportPrompts.RU.todayAnalysis()));
        assertThat(findPlaceholders(ReportPrompts.EN.insightSummary()))
                .isEqualTo(findPlaceholders(ReportPrompts.RU.insightSummary()));
        assertThat(findPlaceholders(ReportPrompts.EN.insightPatterns()))
                .isEqualTo(findPlaceholders(ReportPrompts.RU.insightPatterns()));
        assertThat(findPlaceholders(ReportPrompts.EN.report()))
                .isEqualTo(findPlaceholders(ReportPrompts.RU.report()));
    }

    @Test
    void responseLanguageLineStaysInDayAndPdfPrompts() {
        assertThat(ReportPrompts.RU.todayAnalysis()).contains("ЯЗЫК ОТВЕТА: %5$s");
        assertThat(ReportPrompts.EN.todayAnalysis()).contains("RESPONSE LANGUAGE: %5$s");
        assertThat(ReportPrompts.RU.report()).contains("ЯЗЫК ОТВЕТА: %2$s");
        assertThat(ReportPrompts.EN.report()).contains("RESPONSE LANGUAGE: %2$s");
    }

    @Test
    void introsAndTipThemesMatchByIndex() {
        assertThat(DayReportPrompt.EN.introFormats()).hasSameSizeAs(DayReportPrompt.RU.introFormats());
        assertThat(DayReportPrompt.EN.tipThemes()).hasSameSizeAs(DayReportPrompt.RU.tipThemes());
        for (int i = 0; i < DayReportPrompt.RU.tipThemes().size(); i++) {
            assertThat(DayReportPrompt.EN.tipThemes().get(i).goals())
                    .as("цели темы %s", DayReportPrompt.RU.tipThemes().get(i).text())
                    .isEqualTo(DayReportPrompt.RU.tipThemes().get(i).goals());
        }
    }

    @Test
    void goalRulesExistForEveryGoalAndDietInBothLanguages() {
        for (GoalPromptRules.Texts texts : List.of(GoalPromptRules.RU, GoalPromptRules.EN)) {
            assertThat(texts.goalNames().keySet()).containsExactlyInAnyOrder(UserNutritionProfile.Goal.values());
            assertThat(texts.goalRules().keySet()).containsExactlyInAnyOrder(UserNutritionProfile.Goal.values());
            assertThat(texts.dietNames().keySet()).containsExactlyInAnyOrder(UserNutritionProfile.DietType.values());
        }
        assertThat(GoalPromptRules.EN.dietRules().keySet()).isEqualTo(GoalPromptRules.RU.dietRules().keySet());
    }

    @Test
    void englishDayPromptHasNoRussianText() {
        // Названия блюд пользователь вводит сам — здесь английские, чтобы проверить только наш текст
        for (UserNutritionProfile.Goal goal : UserNutritionProfile.Goal.values()) {
            for (UserNutritionProfile.DietType diet : UserNutritionProfile.DietType.values()) {
                UserNutritionProfile profile = new UserNutritionProfile()
                        .setSex(UserNutritionProfile.Sex.MALE)
                        .setAgeYears(30)
                        .setHeightCm(180)
                        .setCurrentWeightKg(new BigDecimal("80"))
                        .setActivityLevel(UserNutritionProfile.ActivityLevel.MEDIUM)
                        .setGoal(goal)
                        .setDietType(diet)
                        .setCaloriesNormPerDay(2200)
                        .setProteinsNormGramsPerDay(150);
                for (int seed = 0; seed < 20; seed++) {
                    String prompt = DayReportPrompt.buildPrompt(profile, List.of(englishDish()), USER_TIME,
                            AppLanguage.EN, new Random(seed));
                    assertThat(prompt).as("%s / %s", goal, diet).doesNotContainPattern("[А-Яа-яЁё]");
                }
            }
        }
    }

    @Test
    void englishTemplatesAndRulesHaveNoRussianText() {
        assertThat(ReportPrompts.EN.persona() + ReportPrompts.EN.insightSummary() + ReportPrompts.EN.insightPatterns()
                + ReportPrompts.EN.report() + ReportPrompts.EN.buildPeriodLabel(7)
                + GoalPromptRules.EN.noGoalRules() + String.join("", GoalPromptRules.EN.dietRules().values()))
                .doesNotContainPattern("[А-Яа-яЁё]");
    }

    @Test
    void unknownLanguageFallsBackToRussian() {
        assertThat(ReportPrompts.of(AppLanguage.RU)).isSameAs(ReportPrompts.RU);
        assertThat(ReportPrompts.of(null)).isSameAs(ReportPrompts.RU);
        assertThat(ReportPrompts.of(AppLanguage.EN)).isSameAs(ReportPrompts.EN);
    }

    /** Подстановки String.format: %1$s, %2$d, %% … — набор без повторов. */
    private static Set<String> findPlaceholders(String template) {
        Set<String> placeholders = new TreeSet<>();
        int i = template.indexOf('%');
        while (i >= 0 && i + 1 < template.length()) {
            int end = i + 1;
            while (end < template.length() && !Character.isLetter(template.charAt(end)) && template.charAt(end) != '%') {
                end++;
            }
            placeholders.add(template.substring(i, Math.min(end + 1, template.length())));
            i = template.indexOf('%', end + 1);
        }
        return placeholders;
    }

    private static Dish englishDish() {
        return new Dish()
                .setName("Oatmeal")
                .setCalories(350)
                .setProteins(12)
                .setWeight(250)
                .setCreated(Instant.parse("2026-10-14T05:30:00Z"));
    }
}
