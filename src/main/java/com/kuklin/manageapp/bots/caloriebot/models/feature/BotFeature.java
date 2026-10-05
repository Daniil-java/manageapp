package com.kuklin.manageapp.bots.caloriebot.models.feature;

import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Функциональные возможности бота, доступ к которым регулируется лимитами и подписками.
 * Используется в паре с аннотацией {@link RequiresFeature}.
 */
@RequiredArgsConstructor
@Getter
public enum BotFeature {
    /**
     * Формирование текстового отчета по калориям за текущий день.
     */
    REPORT_DAY("ИИ-разбор дня"),
    /**
     * Генерация и отправка PDF-отчета со статистикой питания за неделю.
     */
    REPORT_PDF_WEEK("Недельный PDF-отчёт"),
    /**
     * Генерация и отправка подробного PDF-отчета за месяц.
     */
    REPORT_PDF_MONTH("PDF-отчёт за период"),
    /**
     * Распознавание состава и калорийности блюда по фотографии (AI Vision).
     */
    DISH_AI_VISION("Распознавание блюд по фото"),
    /**
     * Возможность добавления блюд в список «Избранное» для быстрого доступа.
     */
    DISH_FAVORITE_LIST("Сохранённые блюда");

    /**
     * Название функции для пользователя (сообщения о лимитах, экраны подписки).
     */
    private final String displayName;
}
