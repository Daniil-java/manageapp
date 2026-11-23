package com.kuklin.manageapp.payment.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Ассинхронный вызов сервиса, обрабатывающего вебхук.
 * Юкааса требует быстрого ответа, иначе продолжит присылать уведомления
 *
 * Отвечает за:
 * - ассинхроный сервиса обработки вебхуков
 */
@Service
@RequiredArgsConstructor
public class AsyncAgentWebhookService {

    private final YooWebhookService yooWebhookService;

    // Событие уже сохранено в webhook_event — здесь только обработка в отдельном потоке
    @Async
    public void handleAsync(Long webhookEventId) {
        yooWebhookService.handle(webhookEventId);
    }
}
