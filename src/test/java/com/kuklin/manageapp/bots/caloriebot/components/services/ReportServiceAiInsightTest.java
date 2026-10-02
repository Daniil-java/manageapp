package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ReportService#getAiInsightReport} — генерация ИИ-инсайтов для страницы Insights.
 */
class ReportServiceAiInsightTest {

    private static final Long USER_ID = 42L;
    private static final String AI_KEY = "test-ai-key";
    // UTC+7: 18:00 UTC — это уже следующий день у пользователя
    private static final String ZONE = "Asia/Ho_Chi_Minh";
    private static final Instant FROM = Instant.parse("2026-09-26T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-03T00:00:00Z");

    private DishService dishService;
    private OpenAiProviderProcessor ai;
    private ReportService service;

    @BeforeEach
    void setUp() {
        dishService = mock(DishService.class);
        ai = mock(OpenAiProviderProcessor.class);

        UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
        when(profileService.getOrCreateProfile(USER_ID)).thenReturn(new UserNutritionProfile()
                .setUserId(USER_ID)
                .setGoal(UserNutritionProfile.Goal.LOSE_WEIGHT)
                .setCaloriesNormPerDay(2000));

        UserSettingsService settingsService = mock(UserSettingsService.class);
        when(settingsService.getOrCreate(USER_ID)).thenReturn(new UserSettings().setTimezoneId(ZONE));

        UserNutritionProfileEntryService entryService = mock(UserNutritionProfileEntryService.class);
        when(entryService.getAllByUserId(USER_ID)).thenReturn(List.of());

        WeightEntryService weightService = mock(WeightEntryService.class);
        when(weightService.getAllWeightHistory(USER_ID)).thenReturn(List.of());

        TelegramCaloriesBotKeyComponents components = mock(TelegramCaloriesBotKeyComponents.class);
        when(components.getAiKey()).thenReturn(AI_KEY);

        service = new ReportService(dishService, profileService, entryService, settingsService,
                weightService, ai, components);
    }

    @Test
    void lessThanThreeDaysWithMealsFailsWithoutCallingAi() {
        givenDishes(
                dish("2026-10-01T03:00:00Z"),
                dish("2026-10-01T05:00:00Z"),
                dish("2026-10-02T03:00:00Z")
        );

        assertThatThrownBy(() -> service.getAiInsightReport(AiInsightType.WEEKLY_SUMMARY, FROM, TO, USER_ID))
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.AI_INSIGHT_NOT_ENOUGH_DATA);
        verifyNoInteractions(ai);
    }

    @Test
    void daysWithMealsAreCountedInUserTimezone() {
        // В UTC это 2 дня, у пользователя (UTC+7) — 3: 1 окт 17:00, 2 окт 01:00, 3 окт 01:00
        givenDishes(
                dish("2026-10-01T10:00:00Z"),
                dish("2026-10-01T18:00:00Z"),
                dish("2026-10-02T18:00:00Z")
        );
        givenAiAnswers("{\"headline\":\"ok\",\"bullets\":[]}");

        AccessResult<String> result = service.getAiInsightReport(AiInsightType.WEEKLY_SUMMARY, FROM, TO, USER_ID);

        assertThat(result.data()).isEqualTo("{\"headline\":\"ok\",\"bullets\":[]}");
    }

    @Test
    void sendsCompactPayloadWithLocalMealTimeInJsonMode() {
        givenDishes(
                dish("2026-09-30T02:00:00Z"),
                dish("2026-10-01T02:00:00Z"),
                dish("2026-10-02T02:30:00Z")
        );
        givenAiAnswers("{\"patterns\":[]}");

        service.getAiInsightReport(AiInsightType.PATTERNS_WEEK, FROM, TO, USER_ID);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).fetchJsonResponse(eq(AI_KEY), prompt.capture(), eq(BotIdentifier.CALORIE_BOT), anyString(),
                eq(MetricsAiInteractionRecord.AiMessageType.TEXT));

        assertThat(prompt.getValue())
                .contains("последние 7 дней")
                .contains("English")
                // 02:30 UTC → 09:30 у пользователя
                .contains("\"eatenAt\":\"2026-10-02T09:30")
                .contains("\"weightGrams\":250")
                .contains("\"goal\":\"LOSE_WEIGHT\"")
                // служебные поля сущностей в ИИ не уходят
                .doesNotContain("\"userId\"")
                .doesNotContain("aiConfidence")
                .doesNotContain("\"created\"");
    }

    @Test
    void monthlyTypeUsesThirtyDayLabel() {
        givenDishes(dish("2026-09-30T02:00:00Z"), dish("2026-10-01T02:00:00Z"), dish("2026-10-02T02:00:00Z"));
        givenAiAnswers("{\"headline\":\"ok\",\"bullets\":[]}");

        service.getAiInsightReport(AiInsightType.MONTHLY_SUMMARY, FROM, TO, USER_ID);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).fetchJsonResponse(anyString(), prompt.capture(), any(), anyString(), any());
        assertThat(prompt.getValue()).contains("последние 30 дней");
    }

    @Test
    void invalidJsonFromAiReturnsNull() {
        givenDishes(dish("2026-09-30T02:00:00Z"), dish("2026-10-01T02:00:00Z"), dish("2026-10-02T02:00:00Z"));
        givenAiAnswers("Sorry, I can't help with that");

        AccessResult<String> result = service.getAiInsightReport(AiInsightType.WEEKLY_SUMMARY, FROM, TO, USER_ID);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.data()).isNull();
    }

    @Test
    void aiExceptionReturnsNull() {
        givenDishes(dish("2026-09-30T02:00:00Z"), dish("2026-10-01T02:00:00Z"), dish("2026-10-02T02:00:00Z"));
        when(ai.fetchJsonResponse(anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new RuntimeException("Read timed out"));

        AccessResult<String> result = service.getAiInsightReport(AiInsightType.WEEKLY_SUMMARY, FROM, TO, USER_ID);

        assertThat(result.data()).isNull();
    }

    // ── Helpers ────────────────────────────────────────────

    private void givenDishes(Dish... dishes) {
        when(dishService.getAllDishedByUserIdAndPeriod(USER_ID, FROM, TO)).thenReturn(List.of(dishes));
    }

    private void givenAiAnswers(String response) {
        when(ai.fetchJsonResponse(anyString(), anyString(), any(), anyString(), any())).thenReturn(response);
    }

    private static Dish dish(String createdUtc) {
        return new Dish()
                .setId(1L)
                .setUserId(USER_ID)
                .setName("Oatmeal")
                .setCategory(Dish.FoodCategory.values()[0])
                .setCalories(350)
                .setProteins(12)
                .setFats(8)
                .setCarbohydrates(55)
                .setWeight(250)
                .setAiConfidence(90)
                .setCreated(Instant.parse(createdUtc));
    }
}
