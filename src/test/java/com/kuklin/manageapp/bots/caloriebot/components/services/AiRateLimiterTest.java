package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.components.repository.CalorieAiRequestRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.AiRateLimitProperties;
import com.kuklin.manageapp.bots.caloriebot.entities.CalorieAiRequest;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimiterTest {

    private static final Long USER = 1L;
    private static final Long PREMIUM_USER = 2L;

    private MutableClock clock;
    private UserSubscriptionService subscriptions;
    private CalorieAiRequestRepository repository;
    // Строки таблицы calorie_ai_request — их видит фейковый репозиторий
    private List<CalorieAiRequest> rows;
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
        rows = new ArrayList<>();
        repository = buildFakeRepository(rows);
        limiter = new AiRateLimiter(limits, subscriptions, repository, clock);
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
                    assertThat(ex.getParams()).containsEntry(ErrorParams.RETRY_AFTER_SECONDS, 60L);
                });

        clock.advance(Duration.ofMinutes(5));
        acquire(USER, 2);
        assertThatThrownBy(() -> limiter.acquireOrThrow(USER))
                .isInstanceOf(ErrorResponseException.class)
                .extracting(e -> ((ErrorResponseException) e).getErrorStatus())
                .isEqualTo(ErrorStatus.AI_DAILY_LIMIT);
    }

    // ── БД ─────────────────────────────────────────────

    @Test
    void userIsLockedBeforeCountingAndRequestIsSaved() {
        limiter.tryAcquire(USER);

        InOrder order = inOrder(repository);
        order.verify(repository).lockUserRequests(USER);
        order.verify(repository).countByAppUserIdAndCreatedAtAfter(USER, clock.instant().minus(Duration.ofMinutes(1)));
        order.verify(repository).save(any());
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getAppUserId()).isEqualTo(USER);
            assertThat(row.getCreatedAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    void deniedCallIsNotSaved() {
        acquire(USER, 3);

        limiter.tryAcquire(USER);

        assertThat(rows).hasSize(3);
    }

    @Test
    void requestsOlderThanThirtyDaysAreDeleted() {
        acquire(USER, 1);
        clock.advance(Duration.ofDays(29));
        acquire(USER, 1);
        clock.advance(Duration.ofDays(2));

        limiter.deleteOldRequests();

        assertThat(rows).singleElement()
                .extracting(CalorieAiRequest::getCreatedAt)
                .isEqualTo(clock.instant().minus(Duration.ofDays(2)));
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

    /** Репозиторий поверх списка: запросы делают то же, что SQL в CalorieAiRequestRepository. */
    private static CalorieAiRequestRepository buildFakeRepository(List<CalorieAiRequest> rows) {
        CalorieAiRequestRepository repository = mock(CalorieAiRequestRepository.class);
        when(repository.save(any())).thenAnswer(inv -> {
            rows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.countByAppUserIdAndCreatedAtAfter(anyLong(), any())).thenAnswer(inv ->
                (long) findRowsAfter(rows, inv.getArgument(0), inv.getArgument(1)).size());
        when(repository.findByAppUserIdAndCreatedAtAfterOrderByCreatedAtDesc(anyLong(), any(), any())).thenAnswer(inv -> {
            Pageable page = inv.getArgument(2);
            return findRowsAfter(rows, inv.getArgument(0), inv.getArgument(1)).stream()
                    .sorted(Comparator.comparing(CalorieAiRequest::getCreatedAt).reversed())
                    .skip(page.getOffset())
                    .limit(page.getPageSize())
                    .toList();
        });
        when(repository.deleteCreatedBefore(any())).thenAnswer(inv -> {
            Instant border = inv.getArgument(0);
            int before = rows.size();
            rows.removeIf(row -> row.getCreatedAt().isBefore(border));
            return before - rows.size();
        });
        return repository;
    }

    private static List<CalorieAiRequest> findRowsAfter(List<CalorieAiRequest> rows, Long userId, Instant since) {
        return rows.stream()
                .filter(row -> row.getAppUserId().equals(userId) && row.getCreatedAt().isAfter(since))
                .toList();
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
