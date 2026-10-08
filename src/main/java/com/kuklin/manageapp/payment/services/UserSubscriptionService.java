package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.handlers.AdminPaymentUpdateHandler;
import com.kuklin.manageapp.payment.repositories.UserSubscriptionRepository;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionInvalidDataException;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionNotFound;
import com.kuklin.manageapp.payment.services.exceptions.subscription.SubscriptionNotSubscribeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class UserSubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PricingPlanService pricingPlanService;
    //TODO разработать нормальный метод для отправки сообщений
    private final AdminPaymentUpdateHandler adminPaymentUpdateHandler;


    /**
     * Статусы, которые считаются "живыми" и участвуют в очередях
     * EXPIRED / CANCELLED сюда принципиально не входят
     */
    private static final Set<UserSubscription.Status> WORKING_STATUSES =
            EnumSet.of(UserSubscription.Status.ACTIVE, UserSubscription.Status.SCHEDULED);

    /**
     * Быстрая проверка — есть ли сейчас активная подписка
     * Используется в user-flow (бот, UI и т.п.)
     */
    public boolean hasActiveSubscription(Long appUserId, BotIdentifier botIdentifier) {
        return getActiveSubscriptionOrNull(appUserId, botIdentifier) != null;
    }

    /**
     * Возвращает текущую активную подписку пользователя или null
     *
     * Перед поиском:
     *  - синхронизирует статусы (SCHEDULED → ACTIVE, ACTIVE → EXPIRED)
     *
     * Важно: используется только в user-flow, НЕ в cron
     */
    public UserSubscription getActiveSubscriptionOrNull(Long appUserId, BotIdentifier botIdentifier) {
        refreshStatuses(appUserId, botIdentifier);
        Instant now = Instant.now();

        return userSubscriptionRepository
                .findFirstByAppUserIdAndBotIdentifierAndStatusAndStartAtLessThanEqualAndEndAtGreaterThanOrderByStartAtAsc(
                        appUserId,
                        botIdentifier,
                        UserSubscription.Status.ACTIVE,
                        now,
                        now
                )
                .orElse(null);
    }

    /**
     * Возвращает активные + запланированные подписки пользователя
     * Используется для отображения очереди подписок
     */
    public List<UserSubscription> getActiveAndScheduledSubscriptions(Long appUserId, BotIdentifier botIdentifier) {
        refreshStatuses(appUserId, botIdentifier);
        Instant now = Instant.now();

        return userSubscriptionRepository
                .findAllByAppUserIdAndBotIdentifierAndStatusInAndEndAtGreaterThanOrderByStartAtAsc(
                        appUserId,
                        botIdentifier,
                        WORKING_STATUSES,
                        now
                );
    }

    /**
     * Создание подписки по платежу
     *
     * Логика:
     *  - проверяем, что план — подписочный
     *  - обновляем текущие статусы
     *  - строим очередь: новая подписка стартует
     *    либо сейчас, либо сразу после последней активной
     */
    @Transactional
    public UserSubscription createSubscriptionByPayment(Payment payment)
            throws PricingPlanNotFoundException,
            SubscriptionNotSubscribeException,
            SubscriptionInvalidDataException {

        PricingPlan plan = pricingPlanService.getPricingPlanById(payment.getPricingPlanId());

        if (plan.getPayloadType() != PricingPlan.PricingPlanType.SUBSCRIPTION) {
            throw new SubscriptionNotSubscribeException();
        }

        if (plan.getDurationDays() == null || plan.getDurationDays() <= 0) {
            throw new SubscriptionInvalidDataException();
        }

        Long appUserId = payment.getAppUserId();
        BotIdentifier botIdentifier = payment.getBotIdentifier();
        Instant now = Instant.now();

        refreshStatuses(appUserId, botIdentifier);

        // Блокируем очередь, чтобы избежать гонок при покупке
        List<UserSubscription> queue =
                userSubscriptionRepository.findAllByAppUserIdAndBotIdentifierAndStatusInForUpdate(
                        appUserId, botIdentifier, WORKING_STATUSES);

        Instant startAt = queue.stream()
                .map(UserSubscription::getEndAt)
                .filter(end -> end.isAfter(now))
                .max(Instant::compareTo)
                .orElse(now);

        Instant endAt = startAt.plus(plan.getDurationDays(), ChronoUnit.DAYS);

        UserSubscription sub = new UserSubscription()
                .setAppUserId(appUserId)
                .setPricingPlanId(plan.getId())
                .setPaymentId(payment.getId())
                .setBotIdentifier(botIdentifier)
                .setStartAt(startAt)
                .setEndAt(endAt)
                .setStatus(startAt.isAfter(now)
                        ? UserSubscription.Status.SCHEDULED
                        : UserSubscription.Status.ACTIVE);

        UserSubscription userSubscription = userSubscriptionRepository.save(sub);

        try {
            adminPaymentUpdateHandler.sendSubMessageToAdmin(userSubscription);
        } catch (Exception e) {
            log.error("Subscription message error! Cant send message about NEW subscription!");
        }

        return userSubscription;
    }

    //Метод для выдачи подписки пробного периода
    //Одному пользователю - выдается лишь раз
    @Transactional
    public UserSubscription createSubscriptionByFreePlanOrNull(Long appUserId, BotIdentifier botIdentifier) {
        // 1. Получаем сам план
        PricingPlan plan = pricingPlanService.getFreePricingPlanOrNull(botIdentifier);
        if (plan == null) return null;

        // 2. Проверяем, не была ли уже выдана ЭТА конкретная бесплатная подписка
        boolean alreadyUsed = userSubscriptionRepository
                .existsByAppUserIdAndBotIdentifierAndPricingPlanId(appUserId, botIdentifier, plan.getId());

        if (alreadyUsed) {
            return null;
        }

        // 3. Смотрим текущие подписки (Активные + Запланированные)
        List<UserSubscription> currentSubscriptions = getActiveAndScheduledSubscriptions(appUserId, botIdentifier);

        Instant startAt;
        UserSubscription.Status status;

        if (currentSubscriptions.isEmpty()) {
            // Если чисто — стартуем сейчас и сразу активируем
            startAt = Instant.now();
            status = UserSubscription.Status.ACTIVE;
        } else {
            // Если есть подписки — ищем самую позднюю дату окончания
            // Чтобы поставить новую подписку в очередь после самой последней
            startAt = currentSubscriptions.stream()
                    .map(UserSubscription::getEndAt)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.now()); // Fallback (на всякий случай)

            status = UserSubscription.Status.SCHEDULED;
        }

        // 4. Создаем и сохраняем подписку
        long dummyPaymentId = -1l;
        UserSubscription sub = new UserSubscription()
                .setAppUserId(appUserId)
                .setPricingPlanId(plan.getId())
                .setPaymentId(dummyPaymentId)
                .setBotIdentifier(botIdentifier)
                .setStartAt(startAt)
                // Важно: endAt рассчитываем от startAt, который мы вычислили выше
                .setEndAt(startAt.plus(plan.getDurationDays(), ChronoUnit.DAYS))
                .setStatus(status);

        return userSubscriptionRepository.save(sub);
    }

    /**
     * Локальная синхронизация статусов для конкретного пользователя и бота
     *
     * Делает ТОЛЬКО:
     *  - SCHEDULED → ACTIVE (если старт наступил)
     *  - ACTIVE → EXPIRED (если срок вышел)
     *
     * НЕ предназначен для глобального cron
     */
    @Transactional
    protected void refreshStatuses(Long appUserId, BotIdentifier botIdentifier) {
        Instant now = Instant.now();

        List<UserSubscription> subs =
                userSubscriptionRepository.findAllByAppUserIdAndBotIdentifierAndStatusIn(
                        appUserId, botIdentifier, WORKING_STATUSES);

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

    /**
     * Отмена подписки по платежу
     *
     * Важно:
     *  - отмена может затронуть активную или будущую подписку
     *  - после отмены сдвигаем очередь
     */
    @Transactional
    public void cancelByPayment(Payment payment) throws SubscriptionNotFound {
        Long appUserId = payment.getAppUserId();
        BotIdentifier botIdentifier = payment.getBotIdentifier();

        refreshStatuses(appUserId, botIdentifier);

        List<UserSubscription> toCancel =
                userSubscriptionRepository.findAllByPaymentIdAndBotIdentifier(
                        payment.getId(), botIdentifier);

        if (toCancel.isEmpty()) {
            throw new SubscriptionNotFound();
        }

        Instant now = Instant.now();

        for (UserSubscription sub : toCancel) {
            sub.setStatus(UserSubscription.Status.CANCELLED);
            userSubscriptionRepository.save(sub);

            if (!sub.getEndAt().isAfter(now)) {
                continue;
            }

            Instant anchor = now.isAfter(sub.getStartAt())
                    ? now
                    : sub.getStartAt();

            shiftFutureSubscriptions(appUserId, sub, anchor, botIdentifier);
        }

        refreshStatuses(appUserId, botIdentifier);
    }

    /**
     * Сдвиг будущих подписок после отмены
     * Используется ТОЛЬКО из cancelByPayment
     */
    private void shiftFutureSubscriptions(Long appUserId,
                                          UserSubscription cancelled,
                                          Instant anchorStart,
                                          BotIdentifier botIdentifier) {

        List<UserSubscription> queue =
                userSubscriptionRepository.findAllByAppUserIdAndBotIdentifierAndStatusInOrderByStartAtAsc(
                        appUserId, botIdentifier, WORKING_STATUSES);

        queue = queue.stream()
                .filter(s -> s.getStartAt().isAfter(cancelled.getStartAt()))
                .toList();

        Instant current = anchorStart;

        for (UserSubscription sub : queue) {
            long days = ChronoUnit.DAYS.between(sub.getStartAt(), sub.getEndAt());
            if (days <= 0) continue;

            sub.setStartAt(current);
            sub.setEndAt(current.plus(days, ChronoUnit.DAYS));
            current = sub.getEndAt();
        }

        userSubscriptionRepository.saveAll(queue);
    }

    // Ищем пачку тех, чья ACTIVE подписка закончилась
    public Slice<UserSubscription> findExpiredCandidates(int batchSize) {
        return userSubscriptionRepository.findAllByStatusAndEndAtLessThanEqual(
                UserSubscription.Status.ACTIVE,
                Instant.now(),
                PageRequest.of(0, batchSize, Sort.by("id").ascending())
        );
    }

    // Ищем пачку тех, чья SCHEDULED подписка должна начаться
    public Slice<UserSubscription> findScheduledCandidates(int batchSize) {
        Instant now = Instant.now();
        return userSubscriptionRepository.findAllByStatusAndStartAtLessThanEqualAndEndAtGreaterThan(
                UserSubscription.Status.SCHEDULED, now, now,
                PageRequest.of(0, batchSize, Sort.by("id").ascending())
        );
    }

    // REQUIRES_NEW гарантирует: транзакция закроется (и версия проверится) сразу после метода.
    // Если другой поток изменил запись, здесь вылетит OptimisticLockException.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserSubscription> expireOne(Long id) {
        return userSubscriptionRepository.findById(id)
                .filter(s -> s.getStatus() == UserSubscription.Status.ACTIVE)
                .map(sub -> {
                    sub.setStatus(UserSubscription.Status.EXPIRED);
                    return userSubscriptionRepository.save(sub);
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserSubscription> activateOne(Long id) {
        UserSubscription sub = userSubscriptionRepository.findById(id).orElse(null);

        if (sub == null || sub.getStatus() != UserSubscription.Status.SCHEDULED) {
            return Optional.empty();
        }

        // Проверяем, не появилась ли у пользователя активная подписка в этом боте.
        // Это предотвращает наслоение подписок, если крон сработал некорректно.
        if (userSubscriptionRepository.existsByAppUserIdAndBotIdentifierAndStatus(
                sub.getAppUserId(), sub.getBotIdentifier(), UserSubscription.Status.ACTIVE)) {
            log.warn("Cannot activate sub {} for user {}: already has ACTIVE", id, sub.getAppUserId());
            return Optional.empty();
        }

        sub.setStatus(UserSubscription.Status.ACTIVE);
        return Optional.of(userSubscriptionRepository.save(sub));
    }
}
