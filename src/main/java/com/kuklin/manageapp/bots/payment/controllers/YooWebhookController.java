package com.kuklin.manageapp.bots.payment.controllers;


import com.kuklin.manageapp.bots.payment.models.YooWebhook;
import com.kuklin.manageapp.bots.payment.services.YooWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("payment/yookassa/webhooks")
public class YooWebhookController {

    private final YooWebhookService service;

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody YooWebhook payload, HttpServletRequest req) {
        // 1) сразу 200 — ЮKassa этого ждёт
        // 2) обработку делегируем в сервис (он сам разрулит идемпотентность/ошибки)
        log.info("YooKassa webhook request!");
        service.handle(payload, req.getRemoteAddr());
        return ResponseEntity.ok().build();
    }
}
