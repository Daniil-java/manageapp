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
    AI_INSIGHT_NOT_ENOUGH_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough data yet. Log your meals for at least 3 days.")
    ;

    private HttpStatus httpStatus;
    private String message;
}