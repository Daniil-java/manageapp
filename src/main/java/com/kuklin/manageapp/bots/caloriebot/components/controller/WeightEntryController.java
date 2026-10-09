package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.WeightEntryService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WeightEntryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/calorie/weight")
@RequiredArgsConstructor
@Tag(name = "Журнал веса", description = "Учет изменений веса пользователя")
public class WeightEntryController {
    private final WeightEntryService weightEntryService;
    private final UserNutritionProfileService profileService;

    @GetMapping
    @Operation(summary = "История взвешиваний", description = "Возвращает полную историю изменения веса пользователя")
    public List<WeightEntryDto> getAllWeightHistory(@Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return weightEntryService.getAllWeightHistoryDto(appUserId);
    }

    @PutMapping
    @Operation(summary = "Записать вес",
            description = "Добавляет запись о весе, делает его текущим весом профиля и пересчитывает норму КБЖУ и воды")
    public WeightEntryDto updateWeight(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Parameter(description = "Вес в кг", example = "75.5")
            @NotNull @DecimalMin(value = "0.0", inclusive = false) @RequestBody BigDecimal weightKg) {
        return WeightEntryDto.fromEntity(profileService.logWeight(appUserId, weightKg));
    }

    @DeleteMapping("/{weightId}")
    @Operation(summary = "Удаление записи о весе",
            description = "Удаляет запись о весе. Если удалена последняя — вес профиля и норма откатываются к предыдущей записи")
    public void deleteWeight(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Parameter(description = "ID записи веса") @PathVariable(name = "weightId") Long weightId) {
        profileService.deleteWeightEntry(appUserId, weightId);
    }

    @GetMapping("/period")
    @Operation(summary = "Вес за период", description = "Получение записей о весе за определенный период\"")
    public List<WeightEntryDto> getAllWeightByPeriod(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,

            @Parameter(description = "Дата начала (YYYY-MM-DD)", example = "2023-10-01")
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Дата конца (YYYY-MM-DD)", example = "2023-10-07")
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return weightEntryService.getDtoAllWeightByPeriod(appUserId, from, to);
    }
}
