package com.kuklin.manageapp.bots.caloriebot.models.feature;

/**
 * Лимит функции для текущего тарифа пользователя.
 *
 * @param feature     функция бота
 * @param displayName название функции для пользователя
 * @param limit       сколько разрешено за период; -1 — без ограничений, 0 — только по подписке
 * @param remaining   сколько осталось; -1 — без ограничений
 * @param period      период обновления лимита; null, если функция не описана для тарифа
 */
public record FeatureLimitDto(
        BotFeature feature,
        String displayName,
        int limit,
        int remaining,
        FeatureLimitPeriod period
) {
}
