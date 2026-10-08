package com.kuklin.manageapp.bots.caloriebot.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Ограничения на то, что пользователь отправляет в ИИ (текст, фото, голос), и на размер любого запроса.
 * Все значения задаются в application.yaml в блоке calorie.ai-input — других мест с этими цифрами нет.
 * Проверяет их {@link com.kuklin.manageapp.bots.caloriebot.components.services.AiInputValidator}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.ai-input")
public class AiInputLimitsProperties {
    /** Максимальная длина текстового описания блюда, символов. */
    private int textMaxLength;
    /** Максимальная длина подписи к фото, символов. */
    private int photoCommentMaxLength;
    /** Максимальный размер фото (после декодирования Base64). */
    private DataSize photoMaxSize;
    /** Разрешённые форматы фото: jpeg, png, webp, gif. Формат определяется по содержимому файла. */
    private List<String> photoFormats;
    /** Максимальный размер аудио (после декодирования Base64). */
    private DataSize voiceMaxSize;
    /** Максимальная длительность голосового в боте (Telegram сообщает её сам). */
    private Duration voiceMaxDuration;
    /** Разрешённые форматы аудио. */
    private List<String> voiceFormats;
    /** Максимальный размер тела любого HTTP-запроса. */
    private DataSize requestMaxSize;
    /** Сколько последних блюд за период отдаём ИИ в инсайтах — ручными блюдами промпт не раздуть. */
    private int insightMaxDishes;
}
