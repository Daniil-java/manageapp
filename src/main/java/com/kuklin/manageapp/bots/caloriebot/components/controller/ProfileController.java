package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileEntryService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserNutritionProfileDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserNutritionProfileEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/calorie/profile")
@RequiredArgsConstructor
@Tag(name = "Профиль питания", description = "Управление КБЖУ целями и антропометрией пользователя")
public class ProfileController {
    private final UserNutritionProfileService profileService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;
    private final CalorieAccessService calorieAccessService;

    @GetMapping
    @Operation(summary = "Получить профиль", description = "Возвращает текущие настройки профиля питания пользователя")
    public UserNutritionProfileDto getProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return profileService.getOrCreateProfileDto(appUserId);
    }

    @PutMapping
    @Operation(summary = "Обновить профиль", description = "Обновляет данные профиля (рост, вес, возраст, цели)")
    public UserNutritionProfileDto updateProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @Valid @RequestBody UserNutritionProfileDto dto) {
        return profileService.patchProfileDto(appUserId, dto);
    }

    @GetMapping("/entries")
    @Operation(summary = "История целей КБЖУ", description = "Возвращает историю изменений целевых показателей пользователя")
    public List<UserNutritionProfileEntryDto> getAllUserProfilesEntries(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId) {
        return userNutritionProfileEntryService.getAllDtoByUserId(appUserId);
    }

    @GetMapping("/limits")
    @Operation(summary = "Оставшийся лимит попыток", description = "Возвращает количество оставшихся попыток использования функции")
    public int getRemainingLimits(
            @Parameter(hidden = true) @AuthenticationPrincipal Long appUserId,
            @RequestBody BotFeature botFeature) {
        return calorieAccessService.getRemainingLimits(appUserId, botFeature);
    }

}
