package com.kuklin.manageapp.bots.caloriebot.models.feature;

/**
 * Результат проверки доступа к фиче с одновременным списанием попытки
 * ({@link com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService#tryConsume}).
 */
public enum QuotaCharge {
    /** Фичи нет в тарифе или лимит исчерпан — вызывать нельзя. */
    DENIED,
    /** Безлимит — попытка не списывается и не возвращается. */
    UNLIMITED,
    /** Попытка списана; если услуга не оказана — её нужно вернуть. */
    CHARGED
}
