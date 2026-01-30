package com.kuklin.manageapp.bots.caloriebot.featurerestrictions;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Функциональные возможности бота, доступ к которым регулируется лимитами и подписками.
 * Используется в паре с аннотацией {@link com.kuklin.manageapp.bots.caloriebot.featurerestrictions.RequiresFeature}.
 */
@RequiredArgsConstructor
@Getter
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
    DISH_FAVORITE_LIST
}
