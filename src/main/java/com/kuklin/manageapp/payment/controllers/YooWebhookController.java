package com.kuklin.manageapp.payment.controllers;


import com.kuklin.manageapp.payment.models.YooWebhook;
import com.kuklin.manageapp.payment.services.AsyncAgentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//Контроллер вебхуков
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("payment/yookassa/webhooks")
public class YooWebhookController {
    private final AsyncAgentWebhookService asyncAgentWebhookService;

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody YooWebhook payload, HttpServletRequest req) {
        // 1) сразу 200 — ЮKassa этого ждёт
        // 2) обработку делегируем в сервис (он сам разрулит идемпотентность/ошибки)
        log.info("YooKassa webhook request!");
        asyncAgentWebhookService.handleAsync(payload, req.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
