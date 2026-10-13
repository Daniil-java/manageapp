package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Тексты одного PDF-отчёта: язык выбран один раз, дальше построители таблиц и диаграмм
 * просят текст по ключу, не зная про язык.
 */
public record PdfTexts(CalorieTextsService texts, AppLanguage language) {

    /** {{pdf.weekly.title}} в HTML-шаблоне — текст из словаря pdf_*. Заглавные {{AI_ANALYSIS}} — данные, их не трогаем. */
    private static final String KEY_START = "{{pdf.";
    private static final String KEY_END = "}}";
    /** {{LANG}} в шаблоне — код языка для <html lang="…">. */
    private static final String TEMPLATE_LANG = "{{LANG}}";

    public String get(String key) {
        return texts.get(language, key);
    }

    /** Название категории блюда; без категории — «Прочее». */
    public String category(Dish.FoodCategory category) {
        return get((category != null ? category : Dish.FoodCategory.UNKNOWN).getTextKey());
    }

    /** Полная дата для таблиц: 05.10.2026 / Oct 5, 2026. */
    public DateTimeFormatter dateFormat() {
        return DateTimeFormatter.ofPattern(get(PdfTextKeys.DATE), language.getLocale());
    }

    /** Короткая дата для таблиц: 05.10.26 / Oct 5. */
    public DateTimeFormatter shortDateFormat() {
        return DateTimeFormatter.ofPattern(get(PdfTextKeys.DATE_SHORT), language.getLocale());
    }

    /** Подставляет в HTML-шаблон тексты {{pdf.…}} (с экранированием) и код языка {{LANG}}. */
    public String localizeTemplate(String html) {
        String result = html.replace(TEMPLATE_LANG, language.getCode());
        for (String key : templateKeys(html)) {
            result = result.replace("{{" + key + "}}", ReportUtils.escapeHtml(get(key)));
        }
        return result;
    }

    /** Ключи {{pdf.…}} из HTML-шаблона по порядку, без повторов: "<h1>{{pdf.weekly.title}}</h1>" → [pdf.weekly.title]. */
    public static Set<String> templateKeys(String html) {
        Set<String> keys = new LinkedHashSet<>();
        int start = html.indexOf(KEY_START);
        while (start >= 0) {
            int end = html.indexOf(KEY_END, start);
            if (end < 0) break;
            keys.add(html.substring(start + 2, end)); // без «{{»
            start = html.indexOf(KEY_START, end);
        }
        return keys;
    }
}
