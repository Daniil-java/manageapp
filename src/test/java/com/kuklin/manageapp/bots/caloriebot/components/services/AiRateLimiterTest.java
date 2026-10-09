package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimiterTest {

    private static final Long USER = 1L;
    private static final Long PREMIUM_USER = 2L;

    private MutableClock clock;
    private UserSubscriptionService subscriptions;
    private AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AiRateLimitProperties limits = new AiRateLimitProperties();
        limits.setPerMinute(3);
        limits.setPerDay(5);
        limits.setPerDayPremium(8);

        subscriptions = mock(UserSubscriptionService.class);
        when(subscriptions.hasActiveSubscription(PREMIUM_USER, BotIdentifier.CALORIE_BOT)).thenReturn(true);

        clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
        limiter = new AiRateLimiter(limits, subscriptions, clock);
    }

    // ── Минутный лимит ─────────────────────────────────────────────

    @Test
    void minuteLimitBlocksAndTellsWhenToRetry() {
        acquire(USER, 3);
        clock.advance(Duration.ofSeconds(20));

        AiRateLimiter.Decision decision = limiter.tryAcquire(USER);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(AiRateLimiter.Reason.MINUTE);
        assertThat(decision.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void minuteWindowSlides() {
        acquire(USER, 3);
        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire(USER).allowed()).isTrue();
    }

    @Test
    void deniedCallsAreNotCounted() {
        acquire(USER, 3);
        limiter.tryAcquire(USER); // отказ
        limiter.tryAcquire(USER); // отказ
        clock.advance(Duration.ofSeconds(61));

        // отказы не засчитаны: из суточных 5 занято 3 — ещё 2 проходят
        // (если бы отказы считались, было бы занято 5 и следующее не прошло)
        acquire(USER, 2);
    }

    @Test
    void usersDoNotAffectEachOther() {
        acquire(USER, 3);

        assertThat(limiter.tryAcquire(3L).allowed()).isTrue();
    }

    // ── Суточный лимит ─────────────────────────────────────────────

    @Test
    void dailyLimitWithoutSubscription() {
        acquireSpread(USER, 5);

        AiRateLimiter.Decision decision = limiter.tryAcquire(USER);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(AiRateLimiter.Reason.DAY);
        assertThat(decision.premium()).isFalse();
        assertThat(decision.dailyLimit()).isEqualTo(5);
    }

    @Test
    void premiumGetsHigherDailyLimit() {
        acquireSpread(PREMIUM_USER, 8);

        AiRateLimiter.Decision decision = limiter.tryAcquire(PREMIUM_USER);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.premium()).isTrue();
        assertThat(decision.dailyLimit()).isEqualTo(8);
    }

    @Test
    void subscriptionIsCheckedOnlyAfterFreeLimit() {
        acquireSpread(USER, 4);

        verify(subscriptions, never()).hasActiveSubscription(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dailyWindowFreesAfter24HoursFromOldestCall() {
        acquireSpread(USER, 5); // первое — в 10:00, дальше каждые 2 минуты
        AiRateLimiter.Decision denied = limiter.tryAcquire(USER);
        // сейчас 10:10, первое обращение выпадет из окна в 10:00 следующего дня
        assertThat(denied.retryAfterSeconds()).isEqualTo(Duration.ofHours(24).minusMinutes(10).toSeconds());

        clock.advance(Duration.ofSeconds(denied.retryAfterSeconds()));

        assertThat(limiter.tryAcquire(USER).allowed()).isTrue();
    }

    // ── API ─────────────────────────────────────────────

    @Test
    void acquireOrThrowGives429Codes() {
        acquire(USER, 3);
        assertThatThrownBy(() -> limiter.acquireOrThrow(USER))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> {
                    ErrorResponseException ex = (ErrorResponseException) e;
                    assertThat(ex.getErrorStatus()).isEqualTo(ErrorStatus.AI_RATE_LIMIT);
                    assertThat(ex.getClientMessage()).contains("60 seconds");
                });

        clock.advance(Duration.ofMinutes(5));
        acquire(USER, 2);
        assertThatThrownBy(() -> limiter.acquireOrThrow(USER))
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.AI_DAILY_LIMIT);
    }

    // ── Очистка памяти ─────────────────────────────────────────────

    @Test
    void idleUsersAreEvictedAndStartFresh() {
        acquireSpread(USER, 5);
        clock.advance(Duration.ofHours(25));

        limiter.evictIdleUsers();

        acquire(USER, 3);
    }

    // ── Helpers ─────────────────────────────────────────────

    /** n разрешённых обращений подряд. */
    private void acquire(Long user, int n) {
        for (int i = 0; i < n; i++) {
            assertThat(limiter.tryAcquire(user).allowed()).as("call #%d", i + 1).isTrue();
        }
    }

    /** n разрешённых обращений раз в 2 минуты — чтобы не упереться в минутный лимит. */
    private void acquireSpread(Long user, int n) {
        for (int i = 0; i < n; i++) {
            assertThat(limiter.tryAcquire(user).allowed()).as("call #%d", i + 1).isTrue();
            clock.advance(Duration.ofMinutes(2));
        }
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
