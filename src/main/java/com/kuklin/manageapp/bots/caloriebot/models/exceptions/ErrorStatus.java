package com.kuklin.manageapp.bots.caloriebot.models.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus {
    MISSING_FEATURE(HttpStatus.BAD_REQUEST, "Access denied! You need to buy premium!"),
    PROFILE_INSUFFICIENT_DATA(HttpStatus.BAD_REQUEST, "There is not enough data to calculate the value. The fields are empty."),
    USER_NUTRITION_PROFILE_VALIDATION_EXCEPTION(HttpStatus.BAD_REQUEST, "Invalid value!"),
    DISH_NOT_FOUND(HttpStatus.BAD_REQUEST, "Dish not found!"),
    DISH_EMPTY_FOOD_DESCRIPTION(HttpStatus.BAD_REQUEST, "Empty food description!"),
    FAVORITE_DISH_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Favorite dish already exists!"),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "Payment failed!"),
    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "User not found!" ),
    DISH_NOT_BELONG_USER(HttpStatus.BAD_REQUEST, "Dish not belong user!"),
    EMAIL_ALREADY_BUSY(HttpStatus.BAD_REQUEST, "Email is already busy!"),
    SITE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "The site is in closed testing. Access is by invitation only."),
    AI_INSIGHT_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "The report was updated just now. Try again in a minute."),
    AI_INSIGHT_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI could not build the report. Try again later."),
    AI_INSIGHT_NOT_ENOUGH_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough data yet. Log your meals for at least 3 days."),
    // Ограничения ввода для ИИ (calorie.ai-input) — в message подставляется конкретный лимит
    TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "Text is too long."),
    PHOTO_COMMENT_TOO_LONG(HttpStatus.BAD_REQUEST, "Photo comment is too long."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Photo is too large."),
    UNSUPPORTED_IMAGE_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported photo format."),
    AUDIO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Voice message is too long."),
    UNSUPPORTED_AUDIO_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported audio format."),
    INVALID_FILE(HttpStatus.BAD_REQUEST, "The file is damaged or is not valid Base64."),
    REQUEST_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Request is too large."),
    // Ограничение частоты обращений к ИИ (calorie.ai-rate-limit) — в message подставляется, через сколько повторить
    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Too many AI requests. Try again in a minute."),
    AI_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Daily AI limit reached. Try again later."),
    // ИИ не нашёл еду на фото (тариф с лимитом): первый раз за день попытку не списываем, дальше — списываем
    PHOTO_NOT_RECOGNIZED_NOT_COUNTED(HttpStatus.UNPROCESSABLE_ENTITY,
            "No food found in the photo. This scan wasn't counted, but the next unrecognized photo today will be. Take a clear, close-up photo of the dish."),
    PHOTO_NOT_RECOGNIZED_COUNTED(HttpStatus.UNPROCESSABLE_ENTITY,
            "No food found in the photo. This scan was counted. Take a clear, close-up photo of the dish."),
    // Вход на сайт (auth.*): лимиты по IP и неверным паролям; email + пароль выключен до подтверждения email
    AUTH_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later."),
    EMAIL_AUTH_DISABLED(HttpStatus.FORBIDDEN, "Sign-up and sign-in with email are temporarily unavailable. Sign in with Telegram.")
    ;

    private HttpStatus httpStatus;
    private String message;
}