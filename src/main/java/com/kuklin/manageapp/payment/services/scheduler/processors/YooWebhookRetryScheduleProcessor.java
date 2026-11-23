package com.kuklin.manageapp.payment.services.scheduler.processors;

import com.kuklin.manageapp.common.scheduler.ScheduleProcessor;
import com.kuklin.manageapp.payment.services.YooWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Дообработка вебхуков ЮKassa, которые сохранили, но не успели обработать:
 * контроллер ответил 200, а приложение перезапустилось до асинхронной обработки.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YooWebhookRetryScheduleProcessor implements ScheduleProcessor {
    // Моложе — возможно, ещё обрабатывается в @Async прямо сейчас
    static final Duration MIN_AGE = Duration.ofMinutes(5);
    // Старше — бросаем: событие, видимо, падает с откатом транзакции, не крутим его вечно
    static final Duration MAX_AGE = Duration.ofDays(1);

    private final YooWebhookService yooWebhookService;

    @Override
    public void process() {
        List<Long> ids = yooWebhookService.findStuckEventIds(MIN_AGE, MAX_AGE);
        if (ids.isEmpty()) return;
        log.warn("YooKassa webhook retry: {} unhandled events {}", ids.size(), ids);
        for (Long id : ids) {
            try {
                yooWebhookService.handle(id);
            } catch (Exception e) {
                log.error("YooKassa webhook retry failed for event {}", id, e);
            }
        }
    }

    @Override
    public String getSchedulerName() { return getClass().getSimpleName(); }
}
