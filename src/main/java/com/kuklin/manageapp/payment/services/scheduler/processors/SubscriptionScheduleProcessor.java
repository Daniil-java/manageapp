package com.kuklin.manageapp.payment.services.scheduler.processors;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Slice;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduleProcessor implements ScheduleProcessor {
    private final UserSubscriptionService userSubscriptionService;
    private final TelegramBotRegistry botRegistry;
    private final TelegramUserService telegramUserService;

    private static final int BATCH_SIZE = 100;

    @Override
    public void process() {
        // Сначала протухаем старые (освобождаем место для новых активных)
        processBatch(
                userSubscriptionService::findExpiredCandidates,
                userSubscriptionService::expireOne,
                "Срок действия подписки истек!"
        );

        // Затем активируем новые
        processBatch(
                userSubscriptionService::findScheduledCandidates,
                userSubscriptionService::activateOne,
                null // null означает, что используем детальное сообщение ниже
        );
    }

    /**
     * Универсальный метод обработки пачек.
     * Мы всегда берем 0-ю страницу, потому что после изменения статуса
     * записи "улетают" из условий выборки.
     */
    private void processBatch(
            Function<Integer, Slice<UserSubscription>> fetcher,
            Function<Long, Optional<UserSubscription>> processor,
            String staticMsg
    ) {
        boolean hasNext = true;
        while (hasNext) {
            Slice<UserSubscription> slice = fetcher.apply(BATCH_SIZE);
            if (slice.isEmpty()) break;

            for (UserSubscription sub : slice) {
                try {
                    processor.apply(sub.getId()).ifPresent(updated -> {
                        String text = (staticMsg != null) ? staticMsg : getSubscriptionStatusMessage(updated);
                        send(updated, text);
                    });
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("Subscription {} was modified by another process, skipping", sub.getId());
                } catch (Exception e) {
                    log.error("Failed to process subscription {}", sub.getId(), e);
                }
            }
            // Если элементов меньше BATCH_SIZE — мы точно дошли до конца
            hasNext = slice.hasNext();
        }
    }

    private void send(UserSubscription sub, String text) {
        try {
            // Конвертируем AppUserId обратно в Telegram ID для отправки
            TelegramUser tgUser = telegramUserService
                    .findByAppUserIdAndBotIdentifier(sub.getAppUserId(), sub.getBotIdentifier())
                    .orElse(null);

            if (tgUser != null) {
                botRegistry.get(sub.getBotIdentifier())
                        .sendSubExpiredMessage(tgUser.getTelegramId(), text);
            }
        } catch (Exception e) {
            log.error("Telegram notification failed for appUser {}", sub.getAppUserId(), e);
        }
    }

    private String getSubscriptionStatusMessage(UserSubscription sub) {
        ZoneId zoneId = ZoneId.of("UTC");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        String period = (sub.getStartAt() != null && sub.getEndAt() != null)
                ? String.format("%s — %s", sub.getStartAt().atZone(zoneId).format(fmt), sub.getEndAt().atZone(zoneId).format(fmt))
                : "не задан";

        return String.format("Действие подписки началось! \n📌 Статус: %s\n⏳ Период: %s", sub.getStatus().getCommandText(), period);
    }

    @Override
    public String getSchedulerName() { return getClass().getSimpleName(); }
}