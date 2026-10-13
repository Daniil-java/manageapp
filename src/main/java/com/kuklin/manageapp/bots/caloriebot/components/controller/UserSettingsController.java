package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserSettingsDto;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieLanguageSwitcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/calorie/settings")
@RequiredArgsConstructor
@Tag(name = "Настройки пользователя", description = "Управление системными настройками (уведомления, часовой пояс, язык)")
public class UserSettingsController {

    private final UserSettingsService settingsService;
    private final CalorieLanguageSwitcher languageSwitcher;

    @GetMapping
    @Operation(summary = "Получить настройки", description = "Возвращает текущие настройки пользователя")
    public UserSettingsDto getSettings(@Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return settingsService.getSettingsDto(appUserId);
    }

    @PutMapping
    @Operation(summary = "Обновить настройки", description = "Полностью (Put) или частично (Patch) обновляет настройки пользователя")
    public UserSettingsDto updateSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Valid @RequestBody UserSettingsDto dto) {
        return settingsService.updateSettingsDto(appUserId, dto);
    }

    @PutMapping("/language")
    @Operation(summary = "Сменить язык", description = "Язык бота и мини-приложения: ru, en. "
            + "Если язык бота от этого сменился, бот присылает нижнюю клавиатуру на новом языке")
    public UserSettingsDto updateLanguage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @RequestParam(name = "lang") String lang) {
        AppLanguage language = AppLanguage.fromCode(lang)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown language: " + lang));
        languageSwitcher.switchLanguage(appUserId, language);
        return settingsService.getSettingsDto(appUserId);
    }
}
