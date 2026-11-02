package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.handlers.AdminPaymentUpdateHandler;
import com.kuklin.manageapp.payment.repositories.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSubscriptionServiceBonusTest {

    private static final long USER = 7L;
    private static final BotIdentifier BOT = BotIdentifier.CALORIE_BOT;

    private final UserSubscriptionRepository repository = mock(UserSubscriptionRepository.class);
    private final UserSubscriptionService service = new UserSubscriptionService(repository,
            mock(PricingPlanService.class), mock(AdminPaymentUpdateHandler.class), mock(ApplicationEventPublisher.class));

    private static UserSubscription buildSub(long id, UserSubscription.Status status, Instant start, Instant end) {
        return new UserSubscription().setId(id).setAppUserId(USER).setBotIdentifier(BOT)
                .setStatus(status).setStartAt(start).setEndAt(end);
    }

    @Test
    void cancelledActiveBonusShiftsScheduledSubscriptionToNow() {
        Instant now = Instant.now();
        UserSubscription bonus = buildSub(1, UserSubscription.Status.ACTIVE,
                now.minus(Duration.ofDays(5)), now.plus(Duration.ofDays(25)));
        UserSubscription next = buildSub(2, UserSubscription.Status.SCHEDULED,
                bonus.getEndAt(), bonus.getEndAt().plus(30, ChronoUnit.DAYS));
        when(repository.findById(1L)).thenReturn(Optional.of(bonus));
        when(repository.findAllByAppUserIdAndBotIdentifierAndStatusIn(eq(USER), eq(BOT), any()))
                .thenReturn(List.of(bonus, next));
        when(repository.findAllByAppUserIdAndBotIdentifierAndStatusInOrderByStartAtAsc(eq(USER), eq(BOT), any()))
                .thenReturn(List.of(next));

        assertThat(service.cancelBonusSubscription(1L)).isTrue();

        assertThat(bonus.getStatus()).isEqualTo(UserSubscription.Status.CANCELLED);
        // Следующая подписка начинается сразу, свои 30 дней сохраняет
        assertThat(Duration.between(now, next.getStartAt()).abs()).isLessThan(Duration.ofMinutes(1));
        assertThat(ChronoUnit.DAYS.between(next.getStartAt(), next.getEndAt())).isEqualTo(30);
    }

    @Test
    void expiredBonusIsNotCancelled() {
        Instant now = Instant.now();
        UserSubscription bonus = buildSub(1, UserSubscription.Status.EXPIRED,
                now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(10)));
        when(repository.findById(1L)).thenReturn(Optional.of(bonus));

        assertThat(service.cancelBonusSubscription(1L)).isFalse();
        assertThat(bonus.getStatus()).isEqualTo(UserSubscription.Status.EXPIRED);
        verify(repository, never()).save(any());
    }

    @Test
    void missingBonusIsNotCancelled() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThat(service.cancelBonusSubscription(1L)).isFalse();
    }
}
