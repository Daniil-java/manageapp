package com.kuklin.manageapp.bots.caloriebot.components;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFeatureUsageService;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.EmptyResultCharge;
import com.kuklin.manageapp.bots.caloriebot.models.feature.QuotaCharge;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureAccessAspectTest {

    private static final Long USER_ID = 42L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;

    private CalorieAccessService accessService;
    private UserFeatureUsageService usageService;
    private ProceedingJoinPoint joinPoint;
    private FeatureAccessAspect aspect;

    @BeforeEach
    void setUp() throws Throwable {
        accessService = mock(CalorieAccessService.class);
        usageService = mock(UserFeatureUsageService.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{USER_ID, "photo"});
        aspect = new FeatureAccessAspect(accessService, usageService);
    }

    // --- Методы-образцы: из них берём настоящие экземпляры аннотации ---

    @RequiresFeature(value = BotFeature.DISH_AI_VISION, botIdentifier = BotIdentifier.CALORIE_BOT, forgiveEmptyOncePerDay = true)
    void photoSample() {
    }

    @RequiresFeature(value = BotFeature.REPORT_DAY, botIdentifier = BotIdentifier.CALORIE_BOT)
    void reportSample() {
    }

    private static RequiresFeature annotation(String method) throws NoSuchMethodException {
        return FeatureAccessAspectTest.class.getDeclaredMethod(method).getAnnotation(RequiresFeature.class);
    }

    @Test
    void deniedDoesNotCallMethod() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.DENIED);

        Object result = aspect.checkAccess(joinPoint, annotation("photoSample"));

        assertThat(result).isEqualTo(AccessResult.denied(BotFeature.DISH_AI_VISION));
        verify(joinPoint, never()).proceed();
    }

    @Test
    void unlimitedEmptyResultIsNotTouched() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.UNLIMITED);
        when(joinPoint.proceed()).thenReturn(AccessResult.success(null));

        AccessResult<?> result = (AccessResult<?>) aspect.checkAccess(joinPoint, annotation("photoSample"));

        assertThat(result.emptyResultCharge()).isEqualTo(EmptyResultCharge.NONE);
        verify(usageService, never()).refundUsage(any(), any(), any());
        verify(usageService, never()).tryUseDailyGrace(any(), any(), any());
    }

    @Test
    void chargedWithDataKeepsCharge() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.CHARGED);
        AccessResult<List<String>> served = AccessResult.success(List.of("dish"));
        when(joinPoint.proceed()).thenReturn(served);

        Object result = aspect.checkAccess(joinPoint, annotation("photoSample"));

        assertThat(result).isSameAs(served);
        verify(usageService, never()).refundUsage(any(), any(), any());
    }

    @Test
    void exceptionRefundsAndRethrows() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.CHARGED);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("OpenAI is down"));

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint, annotation("photoSample")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI is down");
        verify(usageService).refundUsage(USER_ID, BOT, BotFeature.DISH_AI_VISION);
        verify(usageService, never()).tryUseDailyGrace(any(), any(), any());
    }

    @Test
    void failedRefundDoesNotHideOriginalError() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.CHARGED);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("OpenAI is down"));
        doThrow(new RuntimeException("db down")).when(usageService).refundUsage(any(), any(), any());

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint, annotation("photoSample")))
                .hasMessage("OpenAI is down");
    }

    @Test
    void emptyResultWithoutGraceIsRefunded() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.REPORT_DAY)).thenReturn(QuotaCharge.CHARGED);
        when(joinPoint.proceed()).thenReturn(AccessResult.success(null));

        AccessResult<?> result = (AccessResult<?>) aspect.checkAccess(joinPoint, annotation("reportSample"));

        assertThat(result.emptyResultCharge()).isEqualTo(EmptyResultCharge.NONE);
        verify(usageService).refundUsage(USER_ID, BOT, BotFeature.REPORT_DAY);
        verify(usageService, never()).tryUseDailyGrace(any(), any(), any());
    }

    @Test
    void firstEmptyPhotoOfDayIsForgiven() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.CHARGED);
        when(joinPoint.proceed()).thenReturn(AccessResult.success(null));
        when(usageService.tryUseDailyGrace(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(true);

        AccessResult<?> result = (AccessResult<?>) aspect.checkAccess(joinPoint, annotation("photoSample"));

        assertThat(result.emptyResultCharge()).isEqualTo(EmptyResultCharge.FORGIVEN);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.dataIsNull()).isTrue();
        verify(usageService).refundUsage(USER_ID, BOT, BotFeature.DISH_AI_VISION);
    }

    @Test
    void nextEmptyPhotoOfDayIsCharged() throws Throwable {
        when(accessService.tryConsume(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(QuotaCharge.CHARGED);
        when(joinPoint.proceed()).thenReturn(AccessResult.success(null));
        when(usageService.tryUseDailyGrace(USER_ID, BOT, BotFeature.DISH_AI_VISION)).thenReturn(false);

        AccessResult<?> result = (AccessResult<?>) aspect.checkAccess(joinPoint, annotation("photoSample"));

        assertThat(result.emptyResultCharge()).isEqualTo(EmptyResultCharge.CHARGED);
        verify(usageService, never()).refundUsage(any(), any(), any());
    }

    @Test
    void missingUserIdFailsBeforeCharge() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"no user id"});

        assertThatThrownBy(() -> aspect.checkAccess(joinPoint, annotation("photoSample")))
                .isInstanceOf(RuntimeException.class);
        verify(accessService, never()).tryConsume(any(), any(), any());
    }
}
