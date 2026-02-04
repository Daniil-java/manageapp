package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    // Вся история подписок пользователя
    List<UserSubscription> findAllByTelegramIdOrderByStartAtAsc(Long telegramId);

    // Активные + запланированные подписки, которые ещё не закончились
    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusInAndEndAtGreaterThanOrderByStartAtAsc(
            Long telegramId,
            BotIdentifier botIdentifier,
            Collection<UserSubscription.Status> statuses,
            Instant now
    );

    // Последняя (по времени окончания) активная/запланированная подписка
    Optional<UserSubscription> findFirstByTelegramIdAndBotIdentifierAndStatusInAndEndAtGreaterThanOrderByEndAtDesc(
            Long telegramId,
            BotIdentifier botIdentifier,
            Collection<UserSubscription.Status> statuses,
            Instant now
    );

    // Текущая активная подписка (с учётом интервала)
    Optional<UserSubscription> findFirstByTelegramIdAndBotIdentifierAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanOrderByStartAtAsc(
            Long telegramId,
            BotIdentifier botIdentifier,
            UserSubscription.Status status,
            Instant from,
            Instant to
    );

    // Для внутреннего обновления статусов
    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusIn(
            Long telegramId,
            BotIdentifier botIdentifier,
            Collection<UserSubscription.Status> statuses
    );

    // найти по платежу
    List<UserSubscription> findAllByPaymentIdAndBotIdentifier(Long paymentId, BotIdentifier botIdentifier);

    // все “рабочие” подписки по пользователю, отсортированные по старту
    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusInOrderByStartAtAsc(
            Long telegramId,
            BotIdentifier botIdentifier,
            Collection<UserSubscription.Status> statuses

    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSubscription s WHERE s.telegramId = :tgId " +
            "AND s.botIdentifier = :botId AND s.status IN :statuses")
    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusInForUpdate(
            @Param("tgId") Long tgId,
            @Param("botId") BotIdentifier botId,
            @Param("statuses") Set<UserSubscription.Status> statuses
    );

    List<UserSubscription> findAllByBotIdentifierAndStatus(BotIdentifier botIdentifier, UserSubscription.Status status);
    List<UserSubscription> findAllByStatus(UserSubscription.Status status);

    Slice<UserSubscription> findAllByStatusAndEndAtLessThanEqual(
            UserSubscription.Status status,
            Instant now,
            Pageable pageable
    );

    Slice<UserSubscription> findAllByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
            UserSubscription.Status status,
            Instant lessThanStart,
            Instant greaterThanEnd,
            Pageable pageable
    );

    // Проверка наличия активной подписки (используется при активации запланированной)
    boolean existsByTelegramIdAndBotIdentifierAndStatus(
            Long telegramId,
            BotIdentifier botIdentifier,
            UserSubscription.Status status
    );

    boolean existsByTelegramIdAndBotIdentifierAndPricingPlanId(
            Long telegramId,
            BotIdentifier botIdentifier,
            Long pricingPlanId
    );
}
