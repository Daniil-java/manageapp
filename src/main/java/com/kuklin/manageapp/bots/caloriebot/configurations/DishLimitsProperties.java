package com.kuklin.manageapp.bots.caloriebot.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Лимит на добавление блюд без ИИ (вручную, из избранного). Значения — application.yaml, блок calorie.dish-limits.
 * Блюда от ИИ и так ограничены AiRateLimiter, а эти — нет: без лимита скрипт мог бы забить базу
 * и раздуть промпт инсайтов. Проверяет {@link com.kuklin.manageapp.bots.caloriebot.components.services.DishService}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "calorie.dish-limits")
public class DishLimitsProperties {
    /** Не больше стольких блюд (любых) за последние 24 часа. */
    private int perDay;
}
