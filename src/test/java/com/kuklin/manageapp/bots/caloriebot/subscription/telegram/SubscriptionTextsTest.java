package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionTextsTest {

    private static final Long APP_USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    private final UserSettingsService userSettingsService = mock(UserSettingsService.class);
    private final PricingPlanService pricingPlanService = mock(PricingPlanService.class);
    private final UserSubscriptionService userSubscriptionService = mock(UserSubscriptionService.class);
    private SubscriptionTexts subscriptionTexts;

    @BeforeEach
    void setUp() throws Exception {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), userSettingsService);
        subscriptionTexts = new SubscriptionTexts(texts, new PlanTexts(texts), pricingPlanService,
                userSettingsService, userSubscriptionService);
        when(userSettingsService.getZoneId(APP_USER_ID)).thenReturn(ZoneId.of("UTC"));
        when(pricingPlanService.getPricingPlanById(1L)).thenReturn(new PricingPlan().setCodeForOrderId("FREE_TRIAL").setTitle("Trial"));
        when(pricingPlanService.getPricingPlanById(2L)).thenReturn(
                new PricingPlan().setCodeForOrderId("CALORIE_REFERRAL_INVITER").setTitle("Bonus"));
    }

    @Test
    void noSubscriptionsIsFreePlan() {
        assertThat(subscriptionTexts.buildStatusLine(APP_USER_ID, List.of(), NOW)).contains("бесплатный план");
    }

    @Test
    void premiumEndsAtLastSubscriptionInChainWithDaysLeft() {
        List<UserSubscription> chain = List.of(
                buildSubscription(1L, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2))),
                buildSubscription(2L, NOW.plus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(9))));

        String line = subscriptionTexts.buildStatusLine(APP_USER_ID, chain, NOW);

        assertThat(line).contains("Premium до 18 ноября", "ещё 9 дней");
    }

    @Test
    void lastDayIsLessThanDay() {
        List<UserSubscription> chain = List.of(buildSubscription(1L, NOW.minus(Duration.ofDays(2)), NOW.plus(Duration.ofHours(5))));

        assertThat(subscriptionTexts.buildStatusLine(APP_USER_ID, chain, NOW)).contains("меньше суток");
    }

    @Test
    void detailsListChainParts() {
        when(userSubscriptionService.getActiveAndScheduledSubscriptions(eq(APP_USER_ID), any())).thenReturn(List.of(
                buildSubscription(1L, Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(2))),
                buildSubscription(2L, Instant.now().plus(Duration.ofDays(2)), Instant.now().plus(Duration.ofDays(9)))));

        String details = subscriptionTexts.buildStatusDetails(APP_USER_ID);

        assertThat(details).contains("Из чего сложился срок", "Пробный период — 3 дня", "Бонус за друга — 7 дней");
    }

    @Test
    void otherYearShowsYear() {
        assertThat(subscriptionTexts.formatDate(APP_USER_ID, Instant.parse("2027-02-03T10:00:00Z"), NOW))
                .isEqualTo("3 февраля 2027");
    }

    private static UserSubscription buildSubscription(Long planId, Instant start, Instant end) {
        return new UserSubscription().setPricingPlanId(planId).setStartAt(start).setEndAt(end)
                .setStatus(UserSubscription.Status.ACTIVE);
    }
}
