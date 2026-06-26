package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CaloriePaymentService;
import com.kuklin.manageapp.bots.caloriebot.models.SubscriptionStatusDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PaymentResponse;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/calorie/payment")
@RequiredArgsConstructor
@Tag(name = "Платежи и подписки", description = "Управление тарифами и оплатой через Telegram Stars")
public class CaloriePaymentController {

    private final CaloriePaymentService paymentService;

    @GetMapping("/plans")
    @Operation(summary = "Список тарифов", description = "Получает все доступные планы оплаты (XTR и др.)")
    public List<PricingPlan> getAvailablePlans() {
        return paymentService.getAvailablePlans();
    }

    @PostMapping("/create")
    @Operation(summary = "Создать платеж", description = "Генерирует инвойс-ссылку для оплаты выбранного тарифа")
    public PaymentResponse createPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId, // Переименовано для ясности
            @Parameter(description = "ID тарифного плана", example = "1") @RequestParam Long planId) {
        return paymentService.createPaymentLink(appUserId, planId);
    }

    @GetMapping("/subscription-status")
    @Operation(summary = "Статус подписок", description = "Возвращает список активных и запланированных подписок пользователя")
    public List<SubscriptionStatusDto> getSubscriptionStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) { // Переименовано для ясности
        return paymentService.getSubscriptionStatus(appUserId);
    }
}