package com.kuklin.manageapp.bots.caloriebot.models.feature;

import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;

/**
 * Функциональные возможности бота, доступ к которым регулируется лимитами и подписками.
 * Используется в паре с аннотацией {@link RequiresFeature}.
 */
public enum BotFeature {
    /**
     * Формирование текстового отчета по калориям за текущий день.
     */
    REPORT_DAY,
    /**
     * Генерация и отправка PDF-отчета со статистикой питания за неделю.
     */
    REPORT_PDF_WEEK,
    /**
     * Генерация и отправка подробного PDF-отчета за месяц.
     */
    REPORT_PDF_MONTH,
    /**
     * Распознавание состава и калорийности блюда по фотографии (AI Vision).
     */
    DISH_AI_VISION,
    /**
     * Возможность добавления блюд в список «Избранное» для быстрого доступа.
     */
    DISH_FAVORITE_LIST;

    /** Название функции на языке пользователя — ключ словаря bot_* (бот, лендинг, лимиты в API). */
    public String getTextKey() {
        return "feature." + name();
    }
}
