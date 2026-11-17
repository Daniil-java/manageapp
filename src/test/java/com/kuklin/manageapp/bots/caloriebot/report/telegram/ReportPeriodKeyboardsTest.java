package com.kuklin.manageapp.bots.caloriebot.report.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.TelegramCalorieBotFacade;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.telegram.Command;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReportPeriodKeyboardsTest {

    private static final String NO_ACTION = TelegramCalorieBotFacade.NO_ACTION_CALLBACK;
    private static final String PREFIX = Command.CALORIE_REPORT.getCommandText() + " ";
    /** Понедельник: прошлая неделя — 9–15 ноября (случай из отзыва). */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private final ReportPeriodKeyboards keyboards =
            new ReportPeriodKeyboards(new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class)));

    @Test
    void periodMenuOffersLastWeekLastMonthAndCalendar() {
        InlineKeyboardMarkup menu = keyboards.buildPeriodMenu(AppLanguage.RU, TODAY);

        assertThat(menu.getKeyboard()).extracting(row -> row.get(0).getText()).containsExactly(
                "Прошлая неделя · 9–15 ноября", "Прошлый месяц · октябрь", "🗓 Выбрать даты", "← Назад");
        assertThat(menu.getKeyboard()).extracting(row -> row.get(0).getCallbackData()).containsExactly(
                PREFIX + "RANGE 20261109 20261115", PREFIX + "RANGE 20261001 20261031", PREFIX + "CAL 202611",
                Command.CALORIE_REPORT.getCommandText());
    }

    /** Выбор первого дня: будущие дни — заглушки, следующего месяца нет, предыдущий есть. */
    @Test
    void startCalendarHidesFutureDays() {
        InlineKeyboardMarkup calendar = keyboards.buildCalendar(AppLanguage.RU, TODAY, YearMonth.of(2026, 11), null);
        List<List<InlineKeyboardButton>> rows = calendar.getKeyboard();

        assertThat(rows.get(0)).extracting(InlineKeyboardButton::getText).containsExactly("‹", "Ноябрь 2026", "·");
        assertThat(rows.get(0).get(0).getCallbackData()).isEqualTo(PREFIX + "CAL 202610");
        assertThat(rows.get(1)).extracting(InlineKeyboardButton::getText).containsExactly("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс");
        // 1 ноября 2026 — воскресенье: шесть пустых клеток, потом «1»
        assertThat(rows.get(2).get(6).getText()).isEqualTo("1");
        assertThat(findDay(calendar, "16").getCallbackData()).isEqualTo(PREFIX + "CAL 202611 20261116");
        assertThat(findDay(calendar, "17")).isNull();
        assertThat(rows.get(rows.size() - 1).get(0).getCallbackData()).isEqualTo(PREFIX + "PERIOD");
    }

    /** Выбор последнего дня: от первого до первого + 30, первый — в скобках и тоже выбирается. */
    @Test
    void endCalendarLimitsRangeToMaxDays() {
        LocalDate start = LocalDate.of(2026, 9, 20);
        InlineKeyboardMarkup september = keyboards.buildCalendar(AppLanguage.RU, TODAY, YearMonth.of(2026, 9), start);
        InlineKeyboardMarkup october = keyboards.buildCalendar(AppLanguage.RU, TODAY, YearMonth.of(2026, 10), start);

        assertThat(findDay(september, "19")).isNull();
        assertThat(findDay(september, "[20]").getCallbackData()).isEqualTo(PREFIX + "RANGE 20260920 20260920");
        assertThat(september.getKeyboard().get(0).get(0).getText()).isEqualTo("·");
        assertThat(september.getKeyboard().get(0).get(2).getCallbackData()).isEqualTo(PREFIX + "CAL 202610 20260920");
        // 20 сентября + 30 дней = 20 октября
        assertThat(findDay(october, "20").getCallbackData()).isEqualTo(PREFIX + "RANGE 20260920 20261020");
        assertThat(findDay(october, "21")).isNull();
        assertThat(october.getKeyboard().get(0).get(2).getText()).isEqualTo("·");
    }

    @Test
    void englishCalendar() {
        InlineKeyboardMarkup calendar = keyboards.buildCalendar(AppLanguage.EN, TODAY, YearMonth.of(2026, 11), null);

        assertThat(calendar.getKeyboard().get(0).get(1).getText()).isEqualTo("November 2026");
        assertThat(calendar.getKeyboard().get(1).get(0).getText()).isEqualTo("Mon");
    }

    @Test
    void periodsReadLikeText() {
        Locale ru = AppLanguage.RU.getLocale();
        assertThat(ReportPeriodKeyboards.formatPeriod(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9), ru)).isEqualTo("9 ноября");
        assertThat(ReportPeriodKeyboards.formatPeriod(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), ru)).isEqualTo("9–15 ноября");
        assertThat(ReportPeriodKeyboards.formatPeriod(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3), ru))
                .isEqualTo("28 октября – 3 ноября");
        assertThat(ReportPeriodKeyboards.formatPeriod(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5), ru))
                .isEqualTo("20 декабря 2026 – 5 января 2027");
        assertThat(ReportPeriodKeyboards.formatPeriod(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), AppLanguage.EN.getLocale()))
                .isEqualTo("9–15 November");
    }

    /** Telegram принимает callbackData до 64 байт. */
    @Test
    void callbackDataFitsTelegramLimit() {
        String longest = ReportPeriodKeyboards.buildCalendarData(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 9));
        String range = ReportPeriodKeyboards.buildRangeData(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        assertThat(longest.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        assertThat(range.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        assertThat(ReportPeriodKeyboards.parseDate("20261109")).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(ReportPeriodKeyboards.parseMonth("202611")).isEqualTo(YearMonth.of(2026, 11));
    }

    private static InlineKeyboardButton findDay(InlineKeyboardMarkup calendar, String text) {
        return calendar.getKeyboard().stream().skip(2)
                .flatMap(List::stream)
                .filter(b -> text.equals(b.getText()) && !NO_ACTION.equals(b.getCallbackData()))
                .filter(b -> Objects.nonNull(b.getCallbackData()))
                .findFirst().orElse(null);
    }
}
