package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAiInsightService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UiAnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import com.kuklin.manageapp.bots.caloriebot.models.report.AiInsightDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.DashboardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/calorie/reports")
public class ReportController {
    private final UiAnalyticsService uiAnalyticsService;
    private final CalorieAiInsightService calorieAiInsightService;

    @GetMapping("/dashboard")
    @Operation(summary = "Получить данные для дашборда", description = "Возвращает сводку за сегодня и данные для графиков за период")
    public DashboardResponseDto getDashboardData(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return uiAnalyticsService.getDashboardData(appUserId, from, to);
    }

    @GetMapping("/insights")
    @Operation(summary = "Все ИИ-инсайты", description = "Последние сохранённые инсайты всех типов. Без вызова ИИ. Типы, которые ни разу не генерировались, отсутствуют в ответе")
    public Map<AiInsightType, AiInsightDto> getAllInsights(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {

        return calorieAiInsightService.getLatestAll(appUserId);
    }

    @GetMapping("/insights/{type}")
    @Operation(summary = "ИИ-инсайт по типу", description = "Последний сохранённый инсайт. Без вызова ИИ. 204 — если ещё ни разу не генерировался")
    public ResponseEntity<AiInsightDto> getInsight(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Parameter(description = "Тип инсайта", example = "WEEKLY_SUMMARY") @PathVariable(name = "type") AiInsightType type) {

        return calorieAiInsightService.getLatest(appUserId, type)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/insights/{type}/refresh")
    @Operation(summary = "Обновить ИИ-инсайт", description = "Генерирует инсайт заново через ИИ (10–30 секунд). Не чаще раза в минуту на тип — иначе 429")
    public AiInsightDto refreshInsight(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Parameter(description = "Тип инсайта", example = "WEEKLY_SUMMARY") @PathVariable(name = "type") AiInsightType type) {

        return calorieAiInsightService.refresh(appUserId, type);
    }
}
