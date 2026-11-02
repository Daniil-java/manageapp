package com.kuklin.manageapp.bots.caloriebot.models;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Запрос на добавление блюда вручную — без участия ИИ, значения вводит сам пользователь
@Data
@Schema(description = "Запрос на добавление блюда вручную (без ИИ)")
public class ManualDishRequest {
    // ИИ называет блюда подробно («Жареный картофель по-деревенски с овощами и …») — до ~160 символов
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_CALORIES = 5000;
    public static final int MAX_MACRO_GRAMS = 500;
    public static final int MAX_WEIGHT_GRAMS = 5000;
    public static final int MAX_PORTIONS = 20;

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

    @Schema(description = "Вес всего блюда, г; не указан — 1 (как у блюд без веса)", example = "250")
    @Min(value = 1, message = "Вес должен быть не меньше 1 г")
    @Max(value = MAX_WEIGHT_GRAMS, message = "Вес не может быть больше " + MAX_WEIGHT_GRAMS + " г")
    private Integer weight;

    @Schema(description = "Количество порций; по умолчанию 1", example = "1")
    @Min(value = 1, message = "Порций должно быть не меньше 1")
    @Max(value = MAX_PORTIONS, message = "Порций не может быть больше " + MAX_PORTIONS)
    private Integer portions;

    @Schema(description = "Категория блюда; по умолчанию UNKNOWN")
    private Dish.FoodCategory category;
}
