package com.kuklin.manageapp.common.auth.security;

import java.util.Map;

/**
 * Проверка данных входа через Telegram: initData мини-аппы и Telegram Login Widget.
 * Подпись проверяется токенами конкретного бота, поэтому реализация живёт у бота (калорийный TelegramAuthService).
 */
public interface TelegramAuthVerifier {

    /** initData мини-аппы (заголовок X-TG-INIT-DATA): подпись + свежесть auth_date. */
    boolean isValid(String initData);

    /** Данные Telegram Login Widget для входа на сайте: подпись + свежесть auth_date. */
    boolean isValidLoginWidget(Map<String, String> widgetData);

    /** Telegram ID из initData или данных виджета; null — не удалось достать. */
    Long extractTelegramId(String authData);
}
