package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
            LocalDateTime now
    );

    // Последняя (по времени окончания) активная/запланированная подписка
    Optional<UserSubscription> findFirstByTelegramIdAndBotIdentifierAndStatusInAndEndAtGreaterThanOrderByEndAtDesc(
            Long telegramId,
            BotIdentifier botIdentifier,
            Collection<UserSubscription.Status> statuses,
            LocalDateTime now
    );

    // Текущая активная подписка (с учётом интервала)
    Optional<UserSubscription> findFirstByTelegramIdAndBotIdentifierAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanOrderByStartAtAsc(
            Long telegramId,
            BotIdentifier botIdentifier,
            UserSubscription.Status status,
            LocalDateTime from,
            LocalDateTime to
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

    long countByTelegramId(Long telegramId);

//    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusInForUpdate(Long telegramId, BotIdentifier botIdentifier, Set<UserSubscription.Status> workingStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSubscription s WHERE s.telegramId = :tgId " +
            "AND s.botIdentifier = :botId AND s.status IN :statuses")
    List<UserSubscription> findAllByTelegramIdAndBotIdentifierAndStatusInForUpdate(
            @Param("tgId") Long tgId,
            @Param("botId") BotIdentifier botId,
            @Param("statuses") Set<UserSubscription.Status> statuses
    );

}
