package com.kuklin.manageapp.common.web.error;

import lombok.Data;

import java.util.Map;

/**
 * Ошибка для клиента: код + значения для текста. Самого текста здесь нет — он в словарях errors_*:
 * API отдаёт текст из errors_en (GlobalExceptionHandler), бот — на языке пользователя (CalorieTexts.error).
 */
@Data
public class ErrorResponseException extends RuntimeException{
    private final ErrorCode errorCode;
    // Значения для текста ошибки ({max} → 4096): уходят в API (params) и в словарь errors_*.
    // Собирать методами ErrorParams; без значений — пустая карта
    private final Map<String, Object> params;

    public ErrorResponseException(ErrorCode errorCode, Throwable ex) {
        super(errorCode.name(), ex);
        this.errorCode = errorCode;
        this.params = Map.of();
    }

    public ErrorResponseException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public ErrorResponseException(ErrorCode errorCode, Map<String, ?> params) {
        // В логах и стектрейсах: "AI_DAILY_LIMIT {dailyLimit=100, ...}"
        super(params.isEmpty() ? errorCode.name() : errorCode.name() + " " + params);
        this.errorCode = errorCode;
        this.params = Map.copyOf(params);
    }
}
