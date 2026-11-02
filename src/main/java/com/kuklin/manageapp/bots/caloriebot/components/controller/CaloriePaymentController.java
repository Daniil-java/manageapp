package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CaloriePaymentService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.SubscriptionStatusDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PaymentResponse;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PricingPlanDto;
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
    private final CalorieTexts calorieTexts;

    @GetMapping("/plans")
    @Operation(summary = "Список тарифов", description = "Получает все доступные планы оплаты (XTR и др.). "
            + "Название и описание — на языке lang, без него — на языке пользователя")
    public List<PricingPlanDto> getAvailablePlans(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Parameter(description = "Язык названий и описаний: ru, en", example = "en")
            @RequestParam(name = "lang", required = false) String lang) {
        return paymentService.getAvailablePlans(resolveLanguage(lang, appUserId));
    }

    @PostMapping("/create")
    @Operation(summary = "Создать платеж", description = "Генерирует инвойс-ссылку для оплаты выбранного тарифа. "
            + "Название и описание в счёте — на языке lang, без него — на языке пользователя")
    public PaymentResponse createPayment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId, // Переименовано для ясности
            @Parameter(description = "ID тарифного плана", example = "1") @RequestParam(name = "planId") Long planId,
            @Parameter(description = "Язык счёта: ru, en", example = "en")
            @RequestParam(name = "lang", required = false) String lang) {
        return paymentService.createPaymentLink(appUserId, planId, resolveLanguage(lang, appUserId));
    }

    private AppLanguage resolveLanguage(String lang, Long appUserId) {
        return AppLanguage.fromCode(lang).orElseGet(() -> calorieTexts.languageOf(appUserId));
    }

    @GetMapping("/subscription-status")
    @Operation(summary = "Статус подписок", description = "Возвращает список активных и запланированных подписок пользователя")
    public List<SubscriptionStatusDto> getSubscriptionStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) { // Переименовано для ясности
        return paymentService.getSubscriptionStatus(appUserId);
    }
}