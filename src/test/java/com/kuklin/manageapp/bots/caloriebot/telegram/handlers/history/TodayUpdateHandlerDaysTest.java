package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history;

import com.kuklin.manageapp.bots.caloriebot.components.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishKeyboards;
import com.kuklin.manageapp.bots.caloriebot.texts.DishTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.StyledInlineKeyboardButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodayUpdateHandlerDaysTest {

    private static final Long USER_ID = 1L;
    private static final String DAY_PREFIX = "📊СЕГОДНЯ d ";
    private static final String NO_ACTION = TelegramCalorieBotFacade.NO_ACTION_CALLBACK;

    private CalorieTelegramBot bot;
    private DishTexts dishTexts;
    private TodayUpdateHandler handler;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.getZoneId(USER_ID)).thenReturn(ZoneOffset.UTC);
        when(settings.findLanguageCode(USER_ID)).thenReturn("ru");
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), settings);

        AnalyticsService analytics = mock(AnalyticsService.class);
        when(analytics.buildTodayBars(USER_ID)).thenReturn(List.of());
        when(analytics.buildDayBars(eq(USER_ID), any())).thenReturn(List.of());

        dishTexts = mock(DishTexts.class);
        when(dishTexts.buildTodayDiaryText(eq(USER_ID), anyString())).thenReturn("today");
        when(dishTexts.buildDayDiaryText(eq(USER_ID), any(), anyString())).thenReturn("day");

        bot = mock(CalorieTelegramBot.class);
        handler = new TodayUpdateHandler(settings, bot, mock(DishService.class), analytics, dishTexts,
                mock(DishKeyboards.class), texts, new CalorieMenuButtons(texts));
        today = LocalDate.now(ZoneOffset.UTC);
    }

    /** Сегодня: листание сверху (вперёд некуда — заглушка), «Удалить» и «Поделиться» в одном ряду. */
    @Test
    void todayLayout() {
        List<List<InlineKeyboardButton>> rows = handler.getStatsKeyboard(USER_ID).getKeyboard();

        assertThat(rows.get(0)).extracting(InlineKeyboardButton::getText).containsExactly("◀", "Сегодня", "·");
        assertThat(rows.get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly(DAY_PREFIX + today.minusDays(1), "📊СЕГОДНЯ", NO_ACTION);
        assertThat(rows.get(1)).extracting(InlineKeyboardButton::getText).containsExactly("🗑 Удалить", "📸 Поделиться днём");
        assertThat(rows.get(1)).extracting(b -> ((StyledInlineKeyboardButton) b).getStyle()).containsExactly("danger", "primary");
        assertThat(rows.get(2)).extracting(InlineKeyboardButton::getText).containsExactly("← Меню");
    }

    /** Накануне сегодня «▶» ведёт на сегодняшний экран. */
    @Test
    void yesterdayForwardLeadsToToday() {
        List<InlineKeyboardButton> nav = pressAndGetNavRow(today.minusDays(1));

        assertThat(nav).extracting(InlineKeyboardButton::getText).containsExactly("◀", "Сегодня", "▶");
        assertThat(nav).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly(DAY_PREFIX + today.minusDays(2), "📊СЕГОДНЯ", "📊СЕГОДНЯ");
        verify(dishTexts).buildDayDiaryText(eq(USER_ID), eq(today.minusDays(1)), anyString());
    }

    @Test
    void middleDayHasBothNeighbours() {
        List<InlineKeyboardButton> nav = pressAndGetNavRow(today.minusDays(5));

        assertThat(nav).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly(DAY_PREFIX + today.minusDays(6), "📊СЕГОДНЯ", DAY_PREFIX + today.minusDays(4));
    }

    /** Самый ранний день: вместо «◀» заглушка, ряд того же размера. */
    @Test
    void earliestDayHasPlaceholder() {
        List<InlineKeyboardButton> nav = pressAndGetNavRow(today.minusDays(TodayUpdateHandler.MAX_DAYS_BACK));

        assertThat(nav).hasSize(3);
        assertThat(nav.get(0).getText()).isEqualTo("·");
    }

    @Test
    void tooOldDayIsClamped() {
        pressAndGetNavRow(today.minusDays(TodayUpdateHandler.MAX_DAYS_BACK + 10));

        verify(dishTexts).buildDayDiaryText(eq(USER_ID), eq(today.minusDays(TodayUpdateHandler.MAX_DAYS_BACK)), anyString());
    }

    /** Дата из будущего или испорченная — обычный экран «Сегодня». */
    @Test
    void futureOrBrokenDateShowsToday() {
        press(DAY_PREFIX + today.plusDays(3));
        press(DAY_PREFIX + "not-a-date");

        verify(dishTexts, times(2)).buildTodayDiaryText(eq(USER_ID), anyString());
    }

    @Test
    void dayInRange() {
        assertThat(TodayUpdateHandler.getDayInRange(null, today)).isEqualTo(today);
        assertThat(TodayUpdateHandler.getDayInRange(today.plusDays(1), today)).isEqualTo(today);
        assertThat(TodayUpdateHandler.getDayInRange(today.minusDays(3), today)).isEqualTo(today.minusDays(3));
        assertThat(TodayUpdateHandler.getDayInRange(today.minusDays(100), today))
                .isEqualTo(today.minusDays(TodayUpdateHandler.MAX_DAYS_BACK));
    }

    /** Нажимает кнопку дня и возвращает ряд листания — он первый. */
    private List<InlineKeyboardButton> pressAndGetNavRow(LocalDate date) {
        press(DAY_PREFIX + date);
        ArgumentCaptor<InlineKeyboardMarkup> captor = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendEditMessage(anyLong(), anyString(), anyInt(), captor.capture());
        return captor.getValue().getKeyboard().get(0);
    }

    private void press(String data) {
        Chat chat = new Chat();
        chat.setId(100L);
        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(7);
        CallbackQuery query = new CallbackQuery();
        query.setMessage(message);
        query.setData(data);
        Update update = new Update();
        update.setCallbackQuery(query);

        TelegramUser user = new TelegramUser();
        user.setAppUserId(USER_ID);
        handler.handle(update, user);
    }

}
