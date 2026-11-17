package com.kuklin.manageapp.bots.caloriebot.report.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Кнопки выбора периода PDF-отчёта: быстрые «прошлая неделя / прошлый месяц» и календарь
 * (сначала первый день, потом последний). Всё состояние — в callbackData, в базу ничего не пишется.
 * <p>
 * callbackData: «📄АНАЛИТИКА PERIOD» — меню периода; «… CAL 202611» — календарь для первого дня;
 * «… CAL 202611 20261109» — календарь для последнего дня, первый уже выбран; «… RANGE 20261109 20261115» — собрать отчёт.
 */
@Component
@RequiredArgsConstructor
public class ReportPeriodKeyboards {

    public static final String ACTION_PERIOD = "PERIOD";
    public static final String ACTION_CALENDAR = "CAL";
    public static final String ACTION_RANGE = "RANGE";

    /** Самый длинный период отчёта, дней: календарный месяц целиком. */
    public static final int MAX_PERIOD_DAYS = 31;

    private static final DateTimeFormatter DATE_DATA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH_DATA = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String PREV = "‹";
    private static final String NEXT = "›";
    private static final String EMPTY_CELL = " ";

    private final CalorieTexts texts;

    /** Меню периода: прошлая неделя (пн–вс), прошлый месяц, свои даты, назад к выбору отчёта. */
    public InlineKeyboardMarkup buildPeriodMenu(AppLanguage language, LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        LocalDate weekEnd = weekStart.plusDays(6);
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        Locale locale = language.getLocale();

        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(language, BotTextKeys.REPORT_PERIOD_LAST_WEEK, formatPeriod(weekStart, weekEnd, locale)),
                        buildRangeData(weekStart, weekEnd)))
                .row(TelegramKeyboard.button(
                        texts.get(language, BotTextKeys.REPORT_PERIOD_LAST_MONTH,
                                DateTimeFormatter.ofPattern("LLLL", locale).format(lastMonth)),
                        buildRangeData(lastMonth.atDay(1), lastMonth.atEndOfMonth())))
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.REPORT_PERIOD_PICK_DATES),
                        buildCalendarData(YearMonth.from(today), null)))
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_BACK),
                        Command.CALORIE_REPORT.getCommandText()))
                .build();
    }

    /**
     * Календарь месяца. start == null — выбирается первый день: доступны дни до сегодня включительно.
     * start задан — выбирается последний: от start до start + {@value #MAX_PERIOD_DAYS} − 1, не позже сегодня.
     * Недоступные дни — «·», первый день отмечен скобками (его можно выбрать и последним — отчёт за один день).
     */
    public InlineKeyboardMarkup buildCalendar(AppLanguage language, LocalDate today, YearMonth month, LocalDate start) {
        Locale locale = language.getLocale();
        LocalDate lastAllowed = start == null ? today : min(today, start.plusDays(MAX_PERIOD_DAYS - 1));
        YearMonth prev = month.minusMonths(1);
        YearMonth next = month.plusMonths(1);
        boolean hasPrev = start == null || !prev.isBefore(YearMonth.from(start));
        boolean hasNext = !next.isAfter(YearMonth.from(lastAllowed));

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        builder.row(
                hasPrev ? TelegramKeyboard.button(PREV, buildCalendarData(prev, start)) : TelegramKeyboard.placeholderButton(),
                TelegramKeyboard.noActionButton(capitalize(DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month), locale)),
                hasNext ? TelegramKeyboard.button(NEXT, buildCalendarData(next, start)) : TelegramKeyboard.placeholderButton()
        );

        List<InlineKeyboardButton> weekdays = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            weekdays.add(TelegramKeyboard.noActionButton(capitalize(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale), locale)));
        }
        builder.row(weekdays);

        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 1; i < month.atDay(1).getDayOfWeek().getValue(); i++) {
            row.add(TelegramKeyboard.noActionButton(EMPTY_CELL));
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            row.add(buildDayButton(month.atDay(day), start, lastAllowed));
            if (row.size() == 7) {
                builder.row(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            while (row.size() < 7) row.add(TelegramKeyboard.noActionButton(EMPTY_CELL));
            builder.row(row);
        }

        builder.row(TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_BACK), buildPeriodMenuData()));
        return builder.build();
    }

    private InlineKeyboardButton buildDayButton(LocalDate date, LocalDate start, LocalDate lastAllowed) {
        String label = String.valueOf(date.getDayOfMonth());
        if (date.isAfter(lastAllowed) || (start != null && date.isBefore(start))) {
            return TelegramKeyboard.placeholderButton();
        }
        if (start == null) {
            return TelegramKeyboard.button(label, buildCalendarData(YearMonth.from(date), date));
        }
        return TelegramKeyboard.button(date.equals(start) ? "[" + label + "]" : label, buildRangeData(start, date));
    }

    /**
     * Период для людей: «9 ноября», «9–15 ноября», «28 октября – 3 ноября», с годом — если годы разные.
     */
    public static String formatPeriod(LocalDate from, LocalDate to, Locale locale) {
        DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale);
        if (from.equals(to)) return dayMonth.format(from);
        if (YearMonth.from(from).equals(YearMonth.from(to))) return from.getDayOfMonth() + "–" + dayMonth.format(to);
        if (from.getYear() == to.getYear()) return dayMonth.format(from) + " – " + dayMonth.format(to);
        DateTimeFormatter withYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
        return withYear.format(from) + " – " + withYear.format(to);
    }

    public static String buildPeriodMenuData() {
        return getPrefix() + ACTION_PERIOD;
    }

    public static String buildRangeData(LocalDate from, LocalDate to) {
        return getPrefix() + ACTION_RANGE + TelegramBot.DEFAULT_DELIMETER + DATE_DATA.format(from)
                + TelegramBot.DEFAULT_DELIMETER + DATE_DATA.format(to);
    }

    public static String buildCalendarData(YearMonth month, LocalDate start) {
        String data = getPrefix() + ACTION_CALENDAR + TelegramBot.DEFAULT_DELIMETER + MONTH_DATA.format(month);
        return start == null ? data : data + TelegramBot.DEFAULT_DELIMETER + DATE_DATA.format(start);
    }

    /** Дата из callbackData (20261109); java.time.format.DateTimeParseException — если формат не тот. */
    public static LocalDate parseDate(String value) {
        return LocalDate.parse(value, DATE_DATA);
    }

    /** Месяц из callbackData (202611); java.time.format.DateTimeParseException — если формат не тот. */
    public static YearMonth parseMonth(String value) {
        return YearMonth.parse(value, MONTH_DATA);
    }

    private static String getPrefix() {
        return Command.CALORIE_REPORT.getCommandText() + TelegramBot.DEFAULT_DELIMETER;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static String capitalize(String text, Locale locale) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }
}
