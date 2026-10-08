package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.configurations.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    private static final String IP = "1.2.3.4";
    private static final String EMAIL = "user@mail.com";

    private MutableClock clock;
    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        AuthProperties.RateLimit rateLimit = properties.getRateLimit();
        set(rateLimit.getRegister(), 5, Duration.ofHours(1));
        set(rateLimit.getLogin(), 20, Duration.ofMinutes(10));
        set(rateLimit.getTelegram(), 20, Duration.ofMinutes(10));
        set(rateLimit.getFailedPasswordPerEmail(), 10, Duration.ofMinutes(15));

        clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
        limiter = new AuthRateLimiter(properties, clock);
    }

    private static void set(AuthProperties.Limit limit, int max, Duration window) {
        limit.setMax(max);
        limit.setWindow(window);
    }

    @Test
    void registerLimitPerIp() {
        for (int i = 0; i < 5; i++) {
            limiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, IP);
        }

        assertThatThrownBy(() -> limiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, IP))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(e -> {
                    ErrorResponseException ex = (ErrorResponseException) e;
                    assertThat(ex.getErrorStatus()).isEqualTo(ErrorStatus.AUTH_RATE_LIMIT);
                    assertThat(ex.getClientMessage()).isEqualTo("Too many attempts. Try again in 1 h 0 min.");
                });

        // Другой IP и другое действие — свои счётчики
        assertThatCode(() -> limiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, "5.6.7.8")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, IP)).doesNotThrowAnyException();
    }

    @Test
    void windowSlides() {
        for (int i = 0; i < 20; i++) {
            limiter.acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, IP);
            clock.advance(Duration.ofSeconds(10));
        }
        // Первая попытка была 200 секунд назад — выпадет из 10-минутного окна через 400 секунд
        assertThatThrownBy(() -> limiter.acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, IP))
                .hasMessage("Too many attempts. Try again in 7 min.");

        clock.advance(Duration.ofSeconds(400));
        assertThatCode(() -> limiter.acquireIpOrThrow(AuthRateLimiter.Action.LOGIN, IP)).doesNotThrowAnyException();
    }

    @Test
    void emailLockedAfterFailedPasswords() {
        for (int i = 0; i < 9; i++) {
            limiter.recordFailedPassword(EMAIL);
        }
        assertThatCode(() -> limiter.checkEmailNotLockedOrThrow(EMAIL)).doesNotThrowAnyException();

        // Регистр и пробелы не дают обойти блокировку
        limiter.recordFailedPassword("  USER@mail.com ");
        assertThatThrownBy(() -> limiter.checkEmailNotLockedOrThrow(EMAIL))
                .isInstanceOf(ErrorResponseException.class);

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkEmailNotLockedOrThrow(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginResetsFailures() {
        for (int i = 0; i < 9; i++) {
            limiter.recordFailedPassword(EMAIL);
        }
        limiter.resetFailedPasswords(EMAIL);
        limiter.recordFailedPassword(EMAIL);

        assertThatCode(() -> limiter.checkEmailNotLockedOrThrow(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void checkingLockDoesNotCount() {
        for (int i = 0; i < 50; i++) {
            limiter.checkEmailNotLockedOrThrow(EMAIL);
        }
        assertThatCode(() -> limiter.checkEmailNotLockedOrThrow(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void evictIdleRemovesOldKeys() {
        limiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, IP);
        limiter.recordFailedPassword(EMAIL);

        clock.advance(Duration.ofHours(1));
        limiter.evictIdle();

        // После очистки лимит снова полный
        for (int i = 0; i < 5; i++) {
            limiter.acquireIpOrThrow(AuthRateLimiter.Action.REGISTER, IP);
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
