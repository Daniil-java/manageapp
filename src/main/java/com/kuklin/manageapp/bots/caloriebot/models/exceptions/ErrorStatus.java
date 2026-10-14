package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Коды ошибок API и бота. Здесь только HTTP-статус — тексты лежат в словарях
 * i18n/calorie/errors_*.properties по ключу {@link #getTextKey()} (в API message — текст из errors_en).
 */
@Getter
@AllArgsConstructor
public enum ErrorStatus {
    MISSING_FEATURE(HttpStatus.BAD_REQUEST),
    PROFILE_INSUFFICIENT_DATA(HttpStatus.BAD_REQUEST),
    USER_NUTRITION_PROFILE_VALIDATION_EXCEPTION(HttpStatus.BAD_REQUEST),
    DISH_NOT_FOUND(HttpStatus.BAD_REQUEST),
    DISH_EMPTY_FOOD_DESCRIPTION(HttpStatus.BAD_REQUEST),
    FAVORITE_DISH_ALREADY_EXISTS(HttpStatus.BAD_REQUEST),
    FAVORITE_DISH_NOT_FOUND(HttpStatus.NOT_FOUND),
    QUICK_ADD_FULL(HttpStatus.CONFLICT),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST),
    // Оплата только звёздами: счёт оплачивается в Telegram тем же аккаунтом, что привязан к пользователю
    PAYMENT_TELEGRAM_REQUIRED(HttpStatus.FORBIDDEN),
    PRICING_PLAN_NOT_AVAILABLE(HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST),
    DISH_NOT_BELONG_USER(HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_BUSY(HttpStatus.BAD_REQUEST),
    SITE_ACCESS_DENIED(HttpStatus.FORBIDDEN),
    // PUT /calorie/settings: значения проверяются до сохранения — кривая таймзона ломала все запросы пользователя
    INVALID_TIMEZONE(HttpStatus.BAD_REQUEST),
    INVALID_DAILY_SUMMARY_HOUR(HttpStatus.BAD_REQUEST),
    INVALID_MEAL_REMINDER_INTERVAL(HttpStatus.BAD_REQUEST),
    AI_INSIGHT_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS),
    AI_INSIGHT_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    AI_INSIGHT_NOT_ENOUGH_DATA(HttpStatus.UNPROCESSABLE_ENTITY),
    // Ограничения ввода для ИИ (calorie.ai-input) — конкретный лимит в params (ErrorParams)
    TEXT_TOO_LONG(HttpStatus.BAD_REQUEST),
    PHOTO_COMMENT_TOO_LONG(HttpStatus.BAD_REQUEST),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    AUDIO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_AUDIO_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INVALID_FILE(HttpStatus.BAD_REQUEST),
    REQUEST_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    // Ограничение частоты обращений к ИИ (calorie.ai-rate-limit) — через сколько повторить, в params
    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    AI_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    // ИИ не нашёл еду на фото (тариф с лимитом): первый раз за день попытку не списываем, дальше — списываем
    PHOTO_NOT_RECOGNIZED_NOT_COUNTED(HttpStatus.UNPROCESSABLE_ENTITY),
    PHOTO_NOT_RECOGNIZED_COUNTED(HttpStatus.UNPROCESSABLE_ENTITY),
    // ИИ распознал блюда, но сохранить не вышло (сбой БД) — попытку фото возвращаем
    DISH_SAVE_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    // Блюда без ИИ (вручную, из избранного) — calorie.dish-limits.per-day
    DISH_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    // Вход на сайт (auth.*): лимиты по IP и неверным паролям; email + пароль выключен до подтверждения email
    AUTH_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_AUTH_DISABLED(HttpStatus.FORBIDDEN)
    ;

    private final HttpStatus httpStatus;

    /** Ключ текста ошибки для пользователя в словаре i18n/calorie/errors_*.properties. */
    public String getTextKey() {
        return "error." + name();
    }
}
