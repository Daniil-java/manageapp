package com.kuklin.manageapp.bots.caloriebot.models.feature;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Определяет период обновления лимитов на использование функций бота.
 * Используется в паре с {@link com.kuklin.manageapp.bots.caloriebot.entities.UserFeatureUsage}
 * для определения момента обнуления счетчика использований.
 */
@RequiredArgsConstructor
@Getter
public enum FeatureLimitPeriod {

    /**
     * Лимит обновляется каждые 24 часа.
     * Рекомендуется сбрасывать в 00:00 по установленному часовому поясу (напр. Europe/Moscow).
     */
    DAILY,

    /**
     * Лимит обновляется раз в месяц.
     * Обычно сбрасывается 1-го числа месяца или в дату начала расчетного периода.
     */
    MONTHLY,

    /**
     * Лимит на весь период использования бота.
     * Никогда не сбрасывается автоматически. Подходит для разовых пакетов услуг.
     */
    LIFETIME,

    /**
     * Отсутствие каких-либо ограничений.
     * При этом значении проверка счетчика использований обычно игнорируется.
     */
    UNLIMITED
}
