package com.kuklin.manageapp.bots.caloriebot.dish.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Ограничения на то, что пользователь отправляет в ИИ (текст, фото, голос), и на размер любого запроса.
 * Все значения задаются в application.yaml в блоке calorie.ai-input — других мест с этими цифрами нет.
 * Проверяет их {@link com.kuklin.manageapp.bots.caloriebot.dish.ai.AiInputValidator}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.ai-input")
// Проверка при старте: значение вне диапазона или пропало из application.yaml — приложение не запустится
@Validated
public class AiInputLimitsProperties {
    /** Максимальная длина текстового описания блюда, символов. */
    @Positive
    private int textMaxLength;
    /** Максимальная длина подписи к фото, символов. */
    @Positive
    private int photoCommentMaxLength;
    /** Максимальный размер фото (после декодирования Base64). */
    @NotNull
    private DataSize photoMaxSize;
    /** Разрешённые форматы фото: jpeg, png, webp, gif. Формат определяется по содержимому файла. */
    @NotEmpty
    private List<String> photoFormats;
    /** Максимальный размер аудио (после декодирования Base64). */
    @NotNull
    private DataSize voiceMaxSize;
    /** Максимальная длительность голосового в боте (Telegram сообщает её сам). */
    @NotNull
    private Duration voiceMaxDuration;
    /** Разрешённые форматы аудио. */
    @NotEmpty
    private List<String> voiceFormats;
    /** Сколько последних блюд за период отдаём ИИ в инсайтах — ручными блюдами промпт не раздуть. */
    @Positive
    private int insightMaxDishes;
}
