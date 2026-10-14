package com.kuklin.manageapp.bots.caloriebot.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
