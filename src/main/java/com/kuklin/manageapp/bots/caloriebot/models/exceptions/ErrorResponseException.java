package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import lombok.Data;

import java.util.Map;

/**
 * Ошибка для клиента: код + значения для текста. Самого текста здесь нет — он в словарях errors_*:
 * API отдаёт текст из errors_en (GlobalExceptionHandler), бот — на языке пользователя (CalorieTextsService.error).
 */
@Data
public class ErrorResponseException extends RuntimeException{
    private final ErrorStatus errorStatus;
    // Значения для текста ошибки ({max} → 4096): уходят в API (params) и в словарь errors_*.
    // Собирать методами ErrorParams; без значений — пустая карта
    private final Map<String, Object> params;

    public ErrorResponseException(ErrorStatus errorStatus, Throwable ex) {
        super(errorStatus.name(), ex);
        this.errorStatus = errorStatus;
        this.params = Map.of();
    }

    public ErrorResponseException(ErrorStatus errorStatus) {
        this(errorStatus, Map.of());
    }

    public ErrorResponseException(ErrorStatus errorStatus, Map<String, ?> params) {
        // В логах и стектрейсах: "AI_DAILY_LIMIT {dailyLimit=100, ...}"
        super(params.isEmpty() ? errorStatus.name() : errorStatus.name() + " " + params);
        this.errorStatus = errorStatus;
        this.params = Map.copyOf(params);
    }
}
