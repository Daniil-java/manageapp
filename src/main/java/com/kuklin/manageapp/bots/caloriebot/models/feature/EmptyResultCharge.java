package com.kuklin.manageapp.bots.caloriebot.models.feature;

/**
 * Что стало с попыткой, когда ИИ ничего не распознал (например, на фото нет еды).
 * Заполняется только для фич с лимитом и {@code RequiresFeature.forgiveEmptyOncePerDay = true}.
 */
public enum EmptyResultCharge {
    /** Результат не пустой, безлимит или фича не прощает пустые ответы. */
    NONE,
    /** Первый пустой ответ за день — попытку вернули, пользователя нужно предупредить. */
    FORGIVEN,
    /** Сегодня уже прощали — попытка списана. */
    CHARGED
}
