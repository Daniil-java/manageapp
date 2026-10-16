package com.kuklin.manageapp.common.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Настройки входа на сайт. Значения — в application.yaml, блок auth.
 * Используют {@link com.kuklin.manageapp.common.auth.security.AuthService}
 * и {@link com.kuklin.manageapp.common.auth.security.AuthRateLimiter}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private EmailPassword emailPassword = new EmailPassword();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class EmailPassword {
        /**
         * Регистрация и вход по email + паролю (/auth/register, /auth/login).
         * Выключено, пока нет подтверждения email: без него аккаунты можно плодить скриптом.
         * Вход через Telegram работает всегда.
         */
        private boolean enabled;
    }

    @Data
    public static class RateLimit {
        /** Регистраций с одного IP. */
        private Limit register = new Limit();
        /** Попыток входа по паролю с одного IP (и удачных, и нет). */
        private Limit login = new Limit();
        /** Входов через Telegram с одного IP. */
        private Limit telegram = new Limit();
        /** Неверных паролей для одного email — защита от перебора с разных IP. */
        private Limit failedPasswordPerEmail = new Limit();
    }

    /** Не больше max за последние window. */
    @Data
    public static class Limit {
        private int max;
        private Duration window;
    }
}
