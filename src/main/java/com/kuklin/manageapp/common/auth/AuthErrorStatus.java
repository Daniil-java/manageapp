package com.kuklin.manageapp.common.auth;

import com.kuklin.manageapp.common.web.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Ошибки входа на сайт и доступа к нему. */
@Getter
@AllArgsConstructor
public enum AuthErrorStatus implements ErrorCode {
    EMAIL_ALREADY_BUSY(HttpStatus.BAD_REQUEST),
    SITE_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    // Вход на сайт (auth.*): лимиты по IP и неверным паролям; email + пароль выключен до подтверждения email
    AUTH_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_AUTH_DISABLED(HttpStatus.FORBIDDEN)
    ;

    private final HttpStatus httpStatus;
}
