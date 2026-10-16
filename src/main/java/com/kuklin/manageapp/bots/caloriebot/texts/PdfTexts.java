package com.kuklin.manageapp.bots.caloriebot.texts;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys;
import com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Тексты одного PDF-отчёта: язык выбран один раз, дальше построители блоков и графиков
 * просят текст по ключу и форматируют числа и даты, не зная про язык.
 */
public record PdfTexts(CalorieTexts texts, AppLanguage language) {

    /** {{pdf.disclaimer}} в HTML-шаблоне — текст из словаря pdf_*. Заглавные {{COVER}} — данные, их не трогаем. */
    private static final String KEY_START = "{{pdf.";
    private static final String KEY_END = "}}";
    /** {{LANG}} в шаблоне — код языка для <html lang="…">. */
    private static final String TEMPLATE_LANG = "{{LANG}}";
    /** Типографский минус: дефис рядом с цифрами в PDF смотрится как тире */
    private static final String MINUS = "−";
    /** Неразрывный пробел: разделитель тысяч не должен переносить число на новую строку */
    private static final char NBSP = ' ';
    /** Узкий неразрывный пробел (ru-разделитель тысяч в новых JDK): в шрифте Nunito его нет */
    private static final char NARROW_NBSP = ' ';

    public String get(String key) {
        return texts.get(language, key);
    }

    public String get(String key, Object... args) {
        return texts.get(language, key, args);
    }

    /** Название категории блюда; без категории — «Прочее». */
    public String category(Dish.FoodCategory category) {
        return get((category != null ? category : Dish.FoodCategory.UNKNOWN).getTextKey());
    }

    /** Полная дата: 05.10.2026 / Oct 5, 2026. */
    public DateTimeFormatter dateFormat() {
        return getFormatter(PdfTextKeys.DATE);
    }

    /** Дата по формату из словаря: formatDate(date, PdfTextKeys.DATE_DAY) → 05.10 / Oct 5. */
    public String formatDate(TemporalAccessor date, String formatKey) {
        return getFormatter(formatKey).format(date);
    }

    /** Короткое название дня недели с заглавной: Пн / Mon. */
    public String formatWeekday(DayOfWeek day) {
        String name = day.getDisplayName(TextStyle.SHORT, language.getLocale());
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Целое с разделителем тысяч: 1 850 / 1,850. Отрицательное — с типографским минусом. */
    public String formatInt(long value) {
        return formatNumber(value, 0);
    }

    /** Число с фиксированным числом знаков: 82,4 / 82.4. */
    public String formatDecimal(double value, int digits) {
        return formatNumber(value, digits);
    }

    /** Число со знаком: +0,4 / −1,2; ноль без знака. */
    public String formatSigned(double value, int digits) {
        String number = formatNumber(Math.abs(value), digits);
        if (number.equals(formatNumber(0, digits))) return number;
        return (value > 0 ? "+" : MINUS) + number;
    }

    /** Время из минут от полуночи: 09:40. */
    public String formatMinutes(int minutesOfDay) {
        return String.format("%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    /** Подставляет в HTML-шаблон тексты {{pdf.…}} (с экранированием) и код языка {{LANG}}. */
    public String localizeTemplate(String html) {
        String result = html.replace(TEMPLATE_LANG, language.getCode());
        for (String key : templateKeys(html)) {
            result = result.replace("{{" + key + "}}", ReportHtml.escapeHtml(get(key)));
        }
        return result;
    }

    /** Ключи {{pdf.…}} из HTML-шаблона по порядку, без повторов: "<p>{{pdf.disclaimer}}</p>" → [pdf.disclaimer]. */
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

    private DateTimeFormatter getFormatter(String formatKey) {
        return DateTimeFormatter.ofPattern(get(formatKey), language.getLocale());
    }

    private String formatNumber(double value, int digits) {
        NumberFormat format = NumberFormat.getNumberInstance(language.getLocale());
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        String number = format.format(Math.abs(value)).replace(NARROW_NBSP, NBSP);
        boolean negative = value < 0 && !number.equals(format.format(0));
        return negative ? MINUS + number : number;
    }
}
