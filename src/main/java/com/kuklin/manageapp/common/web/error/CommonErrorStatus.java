package com.kuklin.manageapp.common.web.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Ошибки общих частей приложения — не привязаны к конкретному боту. */
@Getter
@AllArgsConstructor
public enum CommonErrorStatus implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST),
    // Тело запроса больше security.request-max-size (RequestSizeLimitFilter) — лимит в params
    REQUEST_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE)
    ;

    private final HttpStatus httpStatus;
}
