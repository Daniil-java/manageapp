package com.kuklin.manageapp.bots.caloriebot.models;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Запрос на добавление блюда вручную — без участия ИИ, значения вводит сам пользователь
@Data
@Schema(description = "Запрос на добавление блюда вручную (без ИИ)")
public class ManualDishRequest {
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_CALORIES = 5000;
    public static final int MAX_MACRO_GRAMS = 500;

    @Schema(description = "Название блюда", example = "Овсянка с ягодами")
    @NotBlank(message = "Название блюда не может быть пустым")
    @Size(max = MAX_NAME_LENGTH, message = "Название блюда не должно быть длиннее " + MAX_NAME_LENGTH + " символов")
    private String name;

    @Schema(description = "Калории, ккал", example = "350")
    @NotNull(message = "Укажите калории")
    @PositiveOrZero(message = "Калории не могут быть отрицательными")
    @Max(value = MAX_CALORIES, message = "Калорий не может быть больше " + MAX_CALORIES)
    private Integer calories;

    @Schema(description = "Белки, г", example = "12")
    @PositiveOrZero(message = "Белки не могут быть отрицательными")
    @Max(value = MAX_MACRO_GRAMS, message = "Белков не может быть больше " + MAX_MACRO_GRAMS + " г")
    private Integer proteins;

    @Schema(description = "Жиры, г", example = "8")
    @PositiveOrZero(message = "Жиры не могут быть отрицательными")
    @Max(value = MAX_MACRO_GRAMS, message = "Жиров не может быть больше " + MAX_MACRO_GRAMS + " г")
    private Integer fats;

    @Schema(description = "Углеводы, г", example = "55")
    @PositiveOrZero(message = "Углеводы не могут быть отрицательными")
    @Max(value = MAX_MACRO_GRAMS, message = "Углеводов не может быть больше " + MAX_MACRO_GRAMS + " г")
    private Integer carbohydrates;

    @Schema(description = "Категория блюда; по умолчанию UNKNOWN")
    private Dish.FoodCategory category;
}
