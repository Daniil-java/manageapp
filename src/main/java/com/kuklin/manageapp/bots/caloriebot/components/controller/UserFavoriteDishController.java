package com.kuklin.manageapp.bots.caloriebot.components.controller;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserFavoriteDishDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/calorie/favorites")
@RequiredArgsConstructor
@Tag(name = "Избранные блюда", description = "Управление списком любимых блюд пользователя")
public class UserFavoriteDishController {
    private final UserFavoriteDishService userFavoriteDishService;

    @GetMapping
    @Operation(summary = "Получить избранное", description = "Возвращает список всех избранных блюд")
    public List<UserFavoriteDishDto> getAll(@Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId) {
        return userFavoriteDishService.getAllForUserDto(tgUserId);
    }

    @PostMapping("/from-dish")
    @Operation(summary = "Сохранить из истории", description = "Добавляет ранее съеденное блюдо в список избранных по его ID")
    public UserFavoriteDishDto saveFromDish(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "ID существующего блюда") @RequestParam Long dishId
    ) {
        return userFavoriteDishService.saveFromDishDto(tgUserId, dishId);
    }

    @PostMapping("/{favoriteId}/use")
    @Operation(summary = "Добавить из избранного в дневник", description = "Создает новую запись о приеме пищи на основе избранного блюда")
    public DishDto useFavorite(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "ID избранного шаблона") @PathVariable Long favoriteId
    ) {
        return userFavoriteDishService.addDishFromFavoriteDto(tgUserId, favoriteId);
    }

    @DeleteMapping("/{favoriteId}")
    @Operation(summary = "Удалить из избранного", description = "Убирает блюдо из списка избранных")
    public void delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long tgUserId,
            @Parameter(description = "ID избранного шаблона") @PathVariable Long favoriteId
    ) {
        userFavoriteDishService.deleteFavorite(favoriteId, tgUserId);
    }
}
