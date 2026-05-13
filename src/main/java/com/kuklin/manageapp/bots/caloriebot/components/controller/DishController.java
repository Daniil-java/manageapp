package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.models.FoodImageRequest;
import com.kuklin.manageapp.bots.caloriebot.models.FoodTextRequest;
import com.kuklin.manageapp.bots.caloriebot.models.FoodVoiceRequest;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/calorie/dishes")
@RequiredArgsConstructor
@Tag(name = "Блюда (Дневник питания)", description = "Управление приемами пищи пользователя")
public class DishController {
    private final DishService dishService;

    @GetMapping("/period")
    @Operation(summary = "Получить блюда за период")
    public List<DishDto> getDishesByPeriod(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,

            @Parameter(description = "Дата начала (YYYY-MM-DD)", example = "2023-10-01")
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Дата конца (YYYY-MM-DD)", example = "2023-10-07")
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return dishService.getDishesByPeriodDto(tgUserId, from, to);
    }

    @GetMapping("/today")
    @Operation(summary = "Получить блюда за сегодня", description = "Возвращает список приемов пищи за текущий день")
    public List<DishDto> getTodayDishes(@Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId) {
        return dishService.getTodayDishesDto(tgUserId);
    }

    @PostMapping("/text")
    @Operation(summary = "Добавить блюдо через текст", description = "Анализирует текст с помощью ИИ и добавляет блюдо в дневник")
    public List<DishDto> addByText(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Valid @RequestBody FoodTextRequest request) {
        return dishService.getDishDtoByDescriptionOrNull(tgUserId, request.getText());
    }

    @PostMapping("/photo")
    @Operation(summary = "Добавить блюдо по фото", description = "Анализирует изображение (Base64) с помощью ИИ и добавляет блюдо")
    public List<DishDto> addByPhoto(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Valid @RequestBody FoodImageRequest request) {
        return dishService.processPhotoAndGetListDto(tgUserId, request.getBase64Image(), request.getMessage());
    }

    @PostMapping("/voice")
    @Operation(summary = "Добавить блюдо голосом", description = "Распознает аудио с помощью ИИ и добавляет блюдо")
    public List<DishDto> addByVoice(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Valid @RequestBody FoodVoiceRequest request
    ) {
        return dishService.processVoiceAndGetListDto(tgUserId, request.getBase64Audio(), request.getFormat());
    }

    @PatchMapping("/{dishId}/portion")
    @Operation(summary = "Обновить порцию блюда", description = "Изменяет размер порции для существующего блюда")
    public DishDto updatePortion(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "ID блюда") @PathVariable Long dishId,
            @Valid @RequestBody DishDto request) {
        return dishService.updateDishPortion(tgUserId, dishId, request);
    }

    @DeleteMapping("/{dishId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить блюдо", description = "Удаляет блюдо из дневника питания по его ID")
    public void deleteDish(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "ID блюда") @PathVariable Long dishId) {
        dishService.removeByDishId(dishId);
    }

    @GetMapping("/streak")
    @Operation(summary = "Удалить блюдо", description = "Удаляет блюдо из дневника питания по его ID")
    public int getDishStreakByDays(@Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId) {
        return dishService.getCurrentStreak(tgUserId);
    }

}
