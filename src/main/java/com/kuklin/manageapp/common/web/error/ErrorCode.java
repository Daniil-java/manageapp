package com.kuklin.manageapp.common.web.error;

import org.springframework.http.HttpStatus;

/**
 * Код ошибки API и бота. Реализуют enum'ы модулей: {@link CommonErrorStatus}, AuthErrorStatus, калорийный ErrorStatus.
 * name() — это контракт: уходит в поле errorCode ответа API (по нему переводит фронт) и в ключ текста ошибки.
 */
public interface ErrorCode {

    String name();

    HttpStatus getHttpStatus();

    /** Ключ текста ошибки для пользователя в словаре errors_*.properties. */
    default String getTextKey() {
        return "error." + name();
    }
}
