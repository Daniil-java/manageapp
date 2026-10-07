package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.bots.caloriebot.models.feature.QuotaCharge;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieAccessServiceTest {

    private static final Long USER_ID = 42L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;
    private static final BotFeature FEATURE = BotFeature.DISH_AI_VISION;

    private PlanFeatureService planFeatureService;
    private UserFeatureUsageService usageService;
    private CalorieAccessService service;

    @BeforeEach
    void setUp() {
        planFeatureService = mock(PlanFeatureService.class);
        usageService = mock(UserFeatureUsageService.class);
        service = new CalorieAccessService(mock(TelegramUserService.class), planFeatureService, usageService);
    }

    private void plan(FeatureLimitPeriod period, Integer limit) {
        PlanFeature config = new PlanFeature();
        config.setLimitPeriod(period);
        config.setLimitValue(limit);
        when(planFeatureService.getFeatureByUserIdAndBotIdentifierAndFeatureOrNull(USER_ID, BOT, FEATURE))
                .thenReturn(config);
    }

    @Test
    void featureMissingInPlanIsDenied() {
        assertThat(service.tryConsume(USER_ID, BOT, FEATURE)).isEqualTo(QuotaCharge.DENIED);
        verify(usageService, never()).tryConsume(any(), any(), any(), any(), anyInt());
    }

    @Test
    void unlimitedPlanIsNotCounted() {
        plan(FeatureLimitPeriod.UNLIMITED, 0);
        assertThat(service.tryConsume(USER_ID, BOT, FEATURE)).isEqualTo(QuotaCharge.UNLIMITED);

        plan(FeatureLimitPeriod.DAILY, -1);
        assertThat(service.tryConsume(USER_ID, BOT, FEATURE)).isEqualTo(QuotaCharge.UNLIMITED);

        verify(usageService, never()).tryConsume(any(), any(), any(), any(), anyInt());
    }

    @Test
    void limitedPlanChargesAtomically() {
        plan(FeatureLimitPeriod.DAILY, 3);
        when(usageService.tryConsume(USER_ID, BOT, FEATURE, FeatureLimitPeriod.DAILY, 3)).thenReturn(true);

        assertThat(service.tryConsume(USER_ID, BOT, FEATURE)).isEqualTo(QuotaCharge.CHARGED);
    }

    @Test
    void exhaustedLimitIsDenied() {
        plan(FeatureLimitPeriod.DAILY, 3);
        when(usageService.tryConsume(USER_ID, BOT, FEATURE, FeatureLimitPeriod.DAILY, 3)).thenReturn(false);

        assertThat(service.tryConsume(USER_ID, BOT, FEATURE)).isEqualTo(QuotaCharge.DENIED);
    }
}
