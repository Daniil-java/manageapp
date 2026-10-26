package com.kuklin.manageapp.bots.caloriebot.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Запрос на распознавание текста (например: "Два яйца и кофе")
@Data
@Schema(description = "Запрос на добавление блюда текстом")
public class FoodTextRequest {
    @Schema(description = "Текстовое описание блюда", example = "Съел тарелку борща и кусок хлеба")
    @NotBlank(message = "Текст описания не может быть пустым")
    private String text;
}
