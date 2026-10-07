package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.payment.models.YooWebhook;
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

    @Async
    public void handleAsync(YooWebhook hook, String remoteAddr) {
        // просто запустить обработку в отдельном потоке
        yooWebhookService.handle(hook, remoteAddr);
    }
}
