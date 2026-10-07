package com.kuklin.manageapp.bots.caloriebot.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Запрос на распознавание фото (передаем base64 или URL)
@Data
@Schema(description = "Запрос на добавление блюда по фото")
public class FoodImageRequest {
    @Schema(description = "Изображение в формате Base64", example = "iVBORw0KGgoAAAANSUhEUgAAAAE...")
    @NotBlank(message = "Изображение обязательно")
    private String base64Image;

    @Schema(description = "Дополнительный комментарий к фото", example = "Это мой завтрак, тут еще сыр спрятан")
    private String message;
}
