package com.kuklin.manageapp.bots.caloriebot.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Сколько обращений к ИИ может сделать один пользователь.
 * Значения — в application.yaml, блок calorie.ai-rate-limit.
 * Проверяет {@link com.kuklin.manageapp.bots.caloriebot.components.services.AiRateLimiter}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.ai-rate-limit")
public class AiRateLimitProperties {
    /** Не больше стольких обращений за последние 60 секунд. */
    private int perMinute;
    /** Не больше стольких обращений за последние 24 часа — без подписки. */
    private int perDay;
    /** Не больше стольких обращений за последние 24 часа — с активной подпиской. */
    private int perDayPremium;
    /**
     * Свой суточный лимит у тарифа: код тарифа (pricing_plans.code_for_order_id) → лимит.
     * Для подписок, купленных на прежних условиях; остальные тарифы — perDayPremium.
     */
    private Map<String, Integer> perDayByPlan = new HashMap<>();

    /** Суточный лимит подписки на тариф planCode. */
    public int getPerDayForPlan(String planCode) {
        return perDayByPlan.getOrDefault(planCode, perDayPremium);
    }
}
