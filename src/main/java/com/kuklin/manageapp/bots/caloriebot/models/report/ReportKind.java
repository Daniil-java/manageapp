package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Вид PDF-отчёта: период и набор разделов.
 * Месяц — с календарём соблюдения нормы, неделя — со списком блюд по дням.
 */
@Getter
@RequiredArgsConstructor
public enum ReportKind {
    WEEK(7, 7, PdfTextKeys.TITLE_WEEK),
    MONTH(30, 14, PdfTextKeys.TITLE_MONTH);

    /** Сколько дней в отчёте, включая сегодняшний */
    private final int days;
    /** На сколько дней вперёд прогноз веса */
    private final int forecastDays;
    /** Заголовок на обложке — ключ словаря pdf_* */
    private final String titleKey;
}
