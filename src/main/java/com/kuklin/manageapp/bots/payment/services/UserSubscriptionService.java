package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.entities.UserSubscription;
import com.kuklin.manageapp.bots.payment.repositories.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserSubscriptionService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PricingPlanService pricingPlanService;
    private static final Set<UserSubscription.Status> WORKING_STATUSES =
            EnumSet.of(UserSubscription.Status.ACTIVE, UserSubscription.Status.SCHEDULED);

    //Проверка, что сейчас есть актуальная подписка
    public boolean hasActiveSubscription(Long telegramId) {
        return getActiveSubscriptionOrNull(telegramId) != null;
    }

    // Текущая активная подписка или null
    public UserSubscription getActiveSubscriptionOrNull(Long telegramId) {
        refreshStatuses(telegramId);
        LocalDateTime now = LocalDateTime.now();

        return userSubscriptionRepository
                .findFirstByTelegramIdAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanOrderByStartAtAsc(
                        telegramId,
                        UserSubscription.Status.ACTIVE,
                        now,
                        now
                )
                .orElse(null);
    }

    /**
     * Возвращает все подписки пользователя, которые:
     * - уже активны ИЛИ запланированы (ACTIVE / SCHEDULED);
     * - ещё не закончились (endAt > now);
     * - отсортированы по startAt (по порядку очереди).
     */
    public List<UserSubscription> getActiveAndScheduledSubscriptions(Long telegramId) {
        refreshStatuses(telegramId);
        LocalDateTime now = LocalDateTime.now();

        return userSubscriptionRepository.findAllByTelegramIdAndStatusInAndEndAtGreaterThanOrderByStartAtAsc(
                telegramId,
                WORKING_STATUSES,
                now
        );
    }

    // Создание новой подписки при покупке

    /**
     * Создаёт подписку по успешному платежу.
     * Логика:
     * - если нет активных/запланированных подписок – подписка начинается "сейчас";
     * - если есть цепочка подписок – новая подписка стартует сразу после последней (endAt последней);
     * - статус: ACTIVE, если startAt <= now, иначе SCHEDULED.
     */
    @Transactional
    public UserSubscription createSubscriptionByPayment(Payment payment) {
        Long pricingPlanId = payment.getPricingPlanId();
        //TODO: ERROR/exception если pricingPlanId == null

        PricingPlan plan = pricingPlanService.getPricingPlanByIdOrNull(pricingPlanId);
        if (plan == null) {
            //TODO: кинуть своё бизнес-исключение
            log.error("PricingPlan not found for id={} when creating subscription for payment id={}",
                    pricingPlanId, payment.getId());
            return null;
        }

        if (plan.getPayloadType() != PricingPlan.PricingPlanType.SUBSCRIPTION) {
            //TODO: кинуть своё бизнес-исключение
            log.error("Attempt to create subscription from non-subscription plan id={}, payment id={}",
                    pricingPlanId, payment.getId());
            return null;
        }

        if (plan.getDurationDays() == null || plan.getDurationDays() <= 0) {
            //TODO: кинуть своё бизнес-исключение
            log.error("Subscription plan id={} has invalid durationDays={}",
                    pricingPlanId, plan.getDurationDays());
            return null;
        }

        Long telegramId = payment.getTelegramId();
        LocalDateTime now = LocalDateTime.now();

        // Обновляем статусы перед расчётом очереди
        refreshStatuses(telegramId);

        // Последняя (по времени окончания) актуальная/запланированная подписка
        Optional<UserSubscription> lastOpt = userSubscriptionRepository
                .findFirstByTelegramIdAndStatusInAndEndAtGreaterThanOrderByEndAtDesc(
                        telegramId,
                        WORKING_STATUSES,
                        now
                );

        LocalDateTime startAt;
        if (lastOpt.isPresent()) {
            // Уже есть цепочка подписок – новая встаёт в хвост очереди
            startAt = lastOpt.get().getEndAt();
        } else {
            // Подписок нет или всё EXPIRED – стартуем сразу
            startAt = now;
        }

        LocalDateTime endAt = startAt.plusDays(plan.getDurationDays());

        UserSubscription subscription = new UserSubscription()
                .setTelegramId(telegramId)
                .setPricingPlanId(plan.getId())
                .setPaymentId(payment.getId())
                .setStartAt(startAt)
                .setEndAt(endAt);

        if (!startAt.isAfter(now)) {
            subscription.setStatus(UserSubscription.Status.ACTIVE);
        } else {
            subscription.setStatus(UserSubscription.Status.SCHEDULED);
        }

        UserSubscription saved = userSubscriptionRepository.save(subscription);

        log.info("Created subscription id={} for telegramId={}, planId={}, startAt={}, endAt={}",
                saved.getId(), telegramId, plan.getId(), saved.getStartAt(), saved.getEndAt());

        return saved;
    }

    // Внутренняя поддержка статусов
    /**
     * Обновляет статусы подписок пользователя по времени:
     * - SCHEDULED → ACTIVE, если startAt <= now < endAt;
     * - ACTIVE → EXPIRED, если endAt <= now.
     *
     * Вызывается:
     * - перед проверкой активной подписки;
     * - перед получением списка активных/запланированных;
     * - перед созданием новой подписки (чтобы очередь была актуальна).
     */
    @Transactional
    protected void refreshStatuses(Long telegramId) {
        LocalDateTime now = LocalDateTime.now();

        List<UserSubscription> subs = userSubscriptionRepository.findAllByTelegramIdAndStatusIn(
                telegramId,
                WORKING_STATUSES
        );

        boolean changed = false;

        for (UserSubscription sub : subs) {
            if (sub.getStatus() == UserSubscription.Status.SCHEDULED
                    && !sub.getStartAt().isAfter(now)
                    && sub.getEndAt().isAfter(now)) {
                sub.setStatus(UserSubscription.Status.ACTIVE);
                changed = true;
            } else if (sub.getStatus() == UserSubscription.Status.ACTIVE
                    && !sub.getEndAt().isAfter(now)) {
                sub.setStatus(UserSubscription.Status.EXPIRED);
                changed = true;
            }
        }

        if (changed) {
            userSubscriptionRepository.saveAll(subs);
        }
    }

    // История подписок
    public List<UserSubscription> getHistory(Long telegramId) {
        return userSubscriptionRepository.findAllByTelegramIdOrderByStartAtAsc(telegramId);
    }

    /**
     * На будущее: обработка возврата денег за подписку.
     * Можно вызывать из логики REFUND:
     * - найти подписку по paymentId,
     * - пометить CANCELLED,
     * - при необходимости подправить очередь подписок.
     */
    @Transactional
    public void cancelByPayment(Payment payment) {
        Long paymentId = payment.getId();
        Long telegramId = payment.getTelegramId();

        if (paymentId == null || telegramId == null) {
            log.error("Cannot cancel subscription: paymentId or telegramId is null. payment={}", payment);
            return;
        }

        // Сначала приводим статусы в актуальное состояние
        refreshStatuses(telegramId);

        List<UserSubscription> toCancel = userSubscriptionRepository.findAllByPaymentId(paymentId);
        if (toCancel.isEmpty()) {
            log.warn("No subscriptions found for paymentId={} (telegramId={})", paymentId, telegramId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (UserSubscription subToCancel : toCancel) {
            if (subToCancel.getStatus() == UserSubscription.Status.CANCELLED) {
                continue;
            }

            UserSubscription.Status oldStatus = subToCancel.getStatus();
            LocalDateTime start = subToCancel.getStartAt();
            LocalDateTime end = subToCancel.getEndAt();

            subToCancel.setStatus(UserSubscription.Status.CANCELLED);
            userSubscriptionRepository.save(subToCancel);

            log.info("Cancelled subscription id={} for paymentId={}, telegramId={}, oldStatus={}, interval=[{}, {}]",
                    subToCancel.getId(), paymentId, telegramId, oldStatus, start, end);

            // Если подписка уже целиком в прошлом — сдвигать нечего
            if (!end.isAfter(now)) {
                continue;
            }

            // Определяем, с какой точки начинать "упаковывать" хвост очереди:
            // - если отменили текущую активную (now между start и end) → следующий слот с now
            // - если отменили будущую (now < start) → следующий слот с start этой подписки
            LocalDateTime anchorStart;
            if (now.isAfter(start)) {
                anchorStart = now;
            } else {
                anchorStart = start;
            }

            // Пересобираем цепочку будущих подписок
            shiftFutureSubscriptions(telegramId, subToCancel, anchorStart);
        }

        // После переразброса дат ещё раз обновим статусы (SCHEDULED → ACTIVE, ACTIVE → EXPIRED)
        refreshStatuses(telegramId);
    }

    private void shiftFutureSubscriptions(Long telegramId,
                                          UserSubscription cancelled,
                                          LocalDateTime anchorStart) {

        // Все ещё живые (ACTIVE/SCHEDULED) подписки пользователя
        List<UserSubscription> queue = userSubscriptionRepository
                .findAllByTelegramIdAndStatusInOrderByStartAtAsc(telegramId, WORKING_STATUSES);

        // Оставляем только те, что идут после отменённой (позже по временной оси)
        queue = queue.stream()
                .filter(sub -> sub.getStartAt().isAfter(cancelled.getStartAt()))
                .toList();

        if (queue.isEmpty()) {
            return;
        }

        LocalDateTime currentStart = anchorStart;

        for (UserSubscription sub : queue) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(sub.getStartAt(), sub.getEndAt());
            if (days <= 0) {
                // На всякий случай защита от кривых данных
                log.warn("Subscription id={} has non-positive duration (start={}, end={}), skipping shift",
                        sub.getId(), sub.getStartAt(), sub.getEndAt());
                continue;
            }

            LocalDateTime newStart = currentStart;
            LocalDateTime newEnd = newStart.plusDays(days);

            if (!newStart.equals(sub.getStartAt()) || !newEnd.equals(sub.getEndAt())) {
                log.info("Shifting subscription id={} for telegramId={} from [{}, {}] to [{}, {}]",
                        sub.getId(), telegramId, sub.getStartAt(), sub.getEndAt(), newStart, newEnd);

                sub.setStartAt(newStart);
                sub.setEndAt(newEnd);
            }

            currentStart = newEnd;
        }

        userSubscriptionRepository.saveAll(queue);
    }

}
