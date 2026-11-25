package com.kuklin.manageapp.aiconversation.models.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;

/**
 * Цены OpenAI в долларах за 1 млн токенов (прайс на 10.2026). Модель ищется по точному имени из запроса.
 * Поменялся прайс или модель — обновить здесь: стоимость пишется в БД в момент вызова и задним числом не пересчитывается.
 */
public enum AiModelPrice {
    GPT51("gpt-5.1", "1.25", "0.125", "10.00"),
    GPT5("gpt-5", "1.25", "0.125", "10.00"),
    GPT4O("gpt-4o", "2.50", "1.25", "10.00"),
    // Вход — аудио-токены ($3), текст подсказки дешевле ($1.25), но его мало: считаем всё по цене аудио
    GPT4O_MINI_TRANSCRIBE("gpt-4o-mini-transcribe", "3.00", "3.00", "5.00");

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final String model;
    private final BigDecimal input;
    private final BigDecimal cachedInput;
    private final BigDecimal output;

    AiModelPrice(String model, String input, String cachedInput, String output) {
        this.model = model;
        this.input = new BigDecimal(input);
        this.cachedInput = new BigDecimal(cachedInput);
        this.output = new BigDecimal(output);
    }

    public static Optional<AiModelPrice> findByModel(String model) {
        return Arrays.stream(values()).filter(p -> p.model.equals(model)).findFirst();
    }

    /**
     * Стоимость вызова в долларах. cachedInputTokens входят в inputTokens (так считает OpenAI)
     * и стоят в 10 раз дешевле обычных.
     */
    public BigDecimal getCostUsd(long inputTokens, long cachedInputTokens, long outputTokens) {
        long cached = Math.min(cachedInputTokens, inputTokens);
        return input.multiply(BigDecimal.valueOf(inputTokens - cached))
                .add(cachedInput.multiply(BigDecimal.valueOf(cached)))
                .add(output.multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
