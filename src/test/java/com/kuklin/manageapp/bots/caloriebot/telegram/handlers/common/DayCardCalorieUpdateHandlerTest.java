package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.DayCardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UtmService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.InviteLinks;
import com.kuklin.manageapp.common.entities.TelegramUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DayCardCalorieUpdateHandlerTest {

    private static final long USER = 7L;
    private static final long CHAT = 1007L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final DayCardService dayCardService = mock(DayCardService.class);
    private DayCardCalorieUpdateHandler handler;
    private TelegramUser user;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(USER)).thenReturn("ru");
        when(settings.getZoneId(USER)).thenReturn(ZoneOffset.UTC);
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), settings);
        ReferralRewardService rewardService = mock(ReferralRewardService.class);
        UtmService utmService = mock(UtmService.class);
        when(bot.getBotUsername()).thenReturn("@calorydairy_bot");
        when(utmService.getOrCreateReferralLink(USER)).thenReturn("ref7");
        InviteLinks links = new InviteLinks(bot, utmService, rewardService, texts);
        handler = new DayCardCalorieUpdateHandler(bot, dayCardService, settings, rewardService, links, texts);
        user = new TelegramUser();
        user.setAppUserId(USER);
    }

    private static Update buildTap(LocalDate date) {
        Chat chat = new Chat();
        chat.setId(CHAT);
        Message message = new Message();
        message.setChat(chat);
        CallbackQuery query = new CallbackQuery();
        query.setMessage(message);
        query.setData(DayCardCalorieUpdateHandler.buildCallbackData(date));
        Update update = new Update();
        update.setCallbackQuery(query);
        return update;
    }

    @Test
    void emptyDayAnswersEveryTap() {
        when(dayCardService.buildCard(eq(USER), eq(DAY), anyString(), anyInt()))
                .thenReturn(new DayCardService.DayCard(DayCardService.Status.EMPTY, null));

        handler.handle(buildTap(DAY), user);
        handler.handle(buildTap(DAY), user);
        handler.handle(buildTap(DAY), user);

        verify(bot, times(3)).sendReturnedMessage(eq(CHAT), eq("За этот день нет блюд — пока нечем поделиться 📸"));
        verify(bot, times(3)).sendChatAction(CHAT, ActionType.UPLOADPHOTO);
    }

    @Test
    void sameDayRightAfterCardSaysItIsAbove() {
        when(dayCardService.buildCard(eq(USER), any(), anyString(), anyInt()))
                .thenReturn(new DayCardService.DayCard(DayCardService.Status.OK, new byte[]{1}));

        handler.handle(buildTap(DAY), user);
        handler.handle(buildTap(DAY), user);
        handler.handle(buildTap(DAY.minusDays(1)), user);

        verify(dayCardService).buildCard(eq(USER), eq(DAY), anyString(), anyInt());
        verify(dayCardService).buildCard(eq(USER), eq(DAY.minusDays(1)), anyString(), anyInt());
        verify(bot).sendReturnedMessage(CHAT, "Карточка этого дня уже выше 👆");
        verify(bot, times(2)).sendPhotoMessage(eq(CHAT), any(), eq("my-day.png"), anyString(), any());
    }

    @Test
    void tapWhileRenderingOnlyRefreshesChatAction() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(dayCardService.buildCard(eq(USER), eq(DAY), anyString(), anyInt())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new DayCardService.DayCard(DayCardService.Status.OK, new byte[]{1});
        });

        Thread first = new Thread(() -> handler.handle(buildTap(DAY), user));
        first.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        handler.handle(buildTap(DAY), user);
        release.countDown();
        first.join(5000);

        verify(dayCardService, times(1)).buildCard(eq(USER), eq(DAY), anyString(), anyInt());
        verify(bot, times(2)).sendChatAction(CHAT, ActionType.UPLOADPHOTO);
        verify(bot, never()).sendReturnedMessage(eq(CHAT), anyString());
    }
}
