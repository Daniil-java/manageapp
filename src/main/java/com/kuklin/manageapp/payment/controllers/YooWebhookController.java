package com.kuklin.manageapp.payment.controllers;


import com.kuklin.manageapp.payment.models.YooWebhook;
import com.kuklin.manageapp.payment.services.AsyncAgentWebhookService;
import com.kuklin.manageapp.payment.services.YooWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP-контроллер для приёма вебхуков от ЮKassa.
 *
 * Отвечает за:
 * - приём уведомления;
 * - логирование факта вызова;
 * - немедленную отдачу HTTP 200, как того требует ЮKassa;
 * - передачу payload в асинхронный сервис для дальнейшей обработки.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("payment/yookassa/webhooks")
@Hidden
public class YooWebhookController {
    private final AsyncAgentWebhookService asyncAgentWebhookService;
    private final YooWebhookService yooWebhookService;

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody YooWebhook payload, HttpServletRequest req) {
        // 1) сохраняем событие в БД до ответа: рестарт после 200 его не потеряет.
        //    Если БД недоступна — исключение даст 5xx, и ЮKassa пришлёт вебхук повторно.
        // 2) сразу 200 — ЮKassa этого ждёт; обработку делегируем в асинхронный сервис
        log.info("YooKassa webhook request!");
        Long eventId = yooWebhookService.saveIncoming(payload, req.getRemoteAddr());
        asyncAgentWebhookService.handleAsync(eventId);
        return ResponseEntity.ok().build();
    }
}
