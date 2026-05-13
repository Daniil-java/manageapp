package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserSettingsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/calorie/settings")
@RequiredArgsConstructor
@Tag(name = "Настройки пользователя", description = "Управление системными настройками (уведомления, часовой пояс)")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    @GetMapping
    @Operation(summary = "Получить настройки", description = "Возвращает текущие настройки пользователя")
    public UserSettingsDto getSettings(@Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId) {
        return settingsService.getSettingsDto(tgUserId);
    }

    @PutMapping
    @Operation(summary = "Обновить настройки", description = "Полностью (Put) или частично (Patch) обновляет настройки пользователя")
    public UserSettingsDto updateSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Valid @RequestBody UserSettingsDto dto) {
        return settingsService.updateSettingsDto(tgUserId, dto);
    }
}
