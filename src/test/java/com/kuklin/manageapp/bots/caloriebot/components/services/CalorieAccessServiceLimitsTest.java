package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitDto;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CalorieAccessServiceLimitsTest {
    private static final Long USER_ID = 42L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;

    private final PlanFeatureService planFeatureService = mock(PlanFeatureService.class);
    private final UserFeatureUsageService usageService = mock(UserFeatureUsageService.class);
    private final CalorieAccessService service = new CalorieAccessService(mock(TelegramUserService.class),
            planFeatureService, usageService, mock(CalorieTexts.class));

    @Test
    void allLimitsReadUsageOnceAndNeverCreateRows() {
        when(planFeatureService.getFeaturesByUserIdAndBotIdentifier(USER_ID, BOT)).thenReturn(List.of(
                feature(BotFeature.DISH_AI_VISION, FeatureLimitPeriod.DAILY, 5),
                feature(BotFeature.REPORT_DAY, FeatureLimitPeriod.DAILY, 1),
                feature(BotFeature.REPORT_PDF_WEEK, FeatureLimitPeriod.UNLIMITED, -1)));
        when(usageService.getUsedCounts(eq(USER_ID), eq(BOT), anyMap()))
                .thenReturn(Map.of(BotFeature.DISH_AI_VISION, 2));

        Map<BotFeature, Integer> remaining = service.getAllLimits(USER_ID).stream()
                .collect(java.util.stream.Collectors.toMap(FeatureLimitDto::feature, FeatureLimitDto::remaining));

        assertThat(remaining.get(BotFeature.DISH_AI_VISION)).isEqualTo(3);
        assertThat(remaining.get(BotFeature.REPORT_DAY)).isEqualTo(1);   // строки нет — ничего не потрачено
        assertThat(remaining.get(BotFeature.REPORT_PDF_WEEK)).isEqualTo(-1);
        assertThat(remaining.get(BotFeature.REPORT_PDF_MONTH)).isZero(); // нет в тарифе
        verify(usageService, times(1)).getUsedCounts(eq(USER_ID), eq(BOT), anyMap());
        verify(usageService, never()).getUserFeatureUsageByUserIdAndBotIdentifierAndBotFeatureOrCreate(any(), any(), any(), any());
    }

    private static PlanFeature feature(BotFeature feature, FeatureLimitPeriod period, int limit) {
        return new PlanFeature().setFeature(feature).setLimitPeriod(period).setLimitValue(limit);
    }
}
