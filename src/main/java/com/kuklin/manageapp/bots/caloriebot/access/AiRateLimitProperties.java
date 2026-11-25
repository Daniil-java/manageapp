package com.kuklin.manageapp.bots.caloriebot.access;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Сколько обращений к ИИ может сделать один пользователь.
 * Значения — в application.yaml, блок calorie.ai-rate-limit.
 * Проверяет {@link com.kuklin.manageapp.bots.caloriebot.access.AiRateLimiter}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.ai-rate-limit")
// Проверка при старте: значение вне диапазона или пропало из application.yaml — приложение не запустится
@Validated
public class AiRateLimitProperties {
    /** Не больше стольких обращений за последние 60 секунд. */
    @Positive
    private int perMinute;
    /** Не больше стольких обращений за последние 24 часа — без подписки. */
    @Positive
    private int perDay;
    /** Не больше стольких обращений за последние 24 часа — с активной подпиской. */
    @Positive
    private int perDayPremium;
    /**
     * Свой суточный лимит у тарифа: код тарифа (pricing_plans.code_for_order_id) → лимит.
     * Для подписок, купленных на прежних условиях; остальные тарифы — perDayPremium.
     */
    @NotNull
    private Map<String, Integer> perDayByPlan = new HashMap<>();

    /** Суточный лимит подписки на тариф planCode. */
    public int getPerDayForPlan(String planCode) {
        return perDayByPlan.getOrDefault(planCode, perDayPremium);
    }
}
