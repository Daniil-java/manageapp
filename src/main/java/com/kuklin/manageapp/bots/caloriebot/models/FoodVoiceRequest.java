package com.kuklin.manageapp.bots.caloriebot.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Запрос на добавление блюда голосом")
public class FoodVoiceRequest {
    @Schema(description = "Аудио в формате Base64", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Аудио обязательно")
    private String base64Audio;

    @Schema(description = "Формат аудио (например, ogg, mp3)", example = "ogg")
    @NotBlank(message = "Формат аудио обязателен")
    private String format;
}
