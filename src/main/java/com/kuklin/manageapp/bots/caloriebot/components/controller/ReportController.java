package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UiAnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.models.report.DashboardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/calorie/reports")
public class ReportController {
    private final UiAnalyticsService uiAnalyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "Получить данные для дашборда", description = "Возвращает сводку за сегодня и данные для графиков за период")
    public DashboardResponseDto getDashboardData(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return uiAnalyticsService.getDashboardData(appUserId, from, to);
    }
}
