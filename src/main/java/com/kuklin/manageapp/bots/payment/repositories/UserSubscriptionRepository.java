package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    // Вся история подписок пользователя
    List<UserSubscription> findAllByTelegramIdOrderByStartAtAsc(Long telegramId);

    // Активные + запланированные подписки, которые ещё не закончились
    List<UserSubscription> findAllByTelegramIdAndStatusInAndEndAtGreaterThanOrderByStartAtAsc(
            Long telegramId,
            Collection<UserSubscription.Status> statuses,
            LocalDateTime now
    );

    // Последняя (по времени окончания) активная/запланированная подписка
    Optional<UserSubscription> findFirstByTelegramIdAndStatusInAndEndAtGreaterThanOrderByEndAtDesc(
            Long telegramId,
            Collection<UserSubscription.Status> statuses,
            LocalDateTime now
    );

    // Текущая активная подписка (с учётом интервала)
    Optional<UserSubscription> findFirstByTelegramIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanOrderByStartAtAsc(
            Long telegramId,
            UserSubscription.Status status,
            LocalDateTime from,
            LocalDateTime to
    );

    // Для внутреннего обновления статусов
    List<UserSubscription> findAllByTelegramIdAndStatusIn(
            Long telegramId,
            Collection<UserSubscription.Status> statuses
    );

    // найти по платежу
    List<UserSubscription> findAllByPaymentId(Long paymentId);

    // все “рабочие” подписки по пользователю, отсортированные по старту
    List<UserSubscription> findAllByTelegramIdAndStatusInOrderByStartAtAsc(
            Long telegramId,
            Collection<UserSubscription.Status> statuses
    );
}
