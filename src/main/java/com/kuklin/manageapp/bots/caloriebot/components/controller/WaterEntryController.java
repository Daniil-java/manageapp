package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.WaterEntryService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WeightEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/calorie/water")
@RequiredArgsConstructor
@Tag(name = "Водный баланс", description = "Учет выпитой воды")
public class WaterEntryController {
    private final WaterEntryService waterEntryService;

    @PostMapping
    @Operation(summary = "Добавить воду", description = "Фиксирует количество выпитой воды в миллилитрах")
    public WaterEntryDto addWater(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "Количество воды (мл)", example = "250") @RequestParam @Min(1) Integer amountMl) {
        return waterEntryService.addWaterDto(tgUserId, amountMl);
    }

    @GetMapping
    @Operation(summary = "Итог за день", description = "Возвращает общее количество выпитой воды за сегодня в миллилитрах")
    public Integer getTodayTotal(@Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId) {
        return waterEntryService.getTodayTotal(tgUserId);
    }

    @GetMapping("/period")
    @Operation(summary = "Вода за период", description = "Получение записей о воде за определенный период")
    public List<WaterEntryDto> getAllWeightByPeriod(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,

            @Parameter(description = "Дата начала (YYYY-MM-DD)", example = "2023-10-01")
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Дата конца (YYYY-MM-DD)", example = "2023-10-07")
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return waterEntryService.getAllWaterEntryByPeriod(tgUserId, from, to);
    }
}
