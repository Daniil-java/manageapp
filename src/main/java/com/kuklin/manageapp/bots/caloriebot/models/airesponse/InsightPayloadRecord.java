package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Данные пользователя за период, которые уходят в ИИ для инсайтов (саммари и шаблоны поведения).
 */
public record InsightPayloadRecord(List<Dish> dishes,
                                   UserNutritionProfile userProfile,
                                   String userSettingsTimezoneId,
                                   List<WeightPoint> weights) {

    public record WeightPoint(LocalDate date, BigDecimal weight) {
    }
}
