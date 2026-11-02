package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalorieLanguageSwitcherTest {

    private static final Long USER = 1L;
    private static final Long CHAT = 100L;

    private CalorieTelegramBot bot;
    private CalorieTexts texts;
    private TelegramUserService telegramUserService;
    private CalorieLanguageSwitcher switcher;

    @BeforeEach
    void setUp() {
        bot = mock(CalorieTelegramBot.class);
        texts = mock(CalorieTexts.class);
        telegramUserService = mock(TelegramUserService.class);
        when(texts.get(any(AppLanguage.class), anyString(), any(Object[].class))).thenReturn("text");
        switcher = new CalorieLanguageSwitcher(bot, mock(CalorieMenuButtons.class), texts, telegramUserService);
    }

    @Test
    void newLanguageIsSavedAndMenuResent() {
        when(texts.languageOf(USER)).thenReturn(AppLanguage.RU);

        switcher.switchLanguage(CHAT, USER, AppLanguage.EN);

        verify(texts).setLanguage(USER, AppLanguage.EN);
        verify(bot).sendReturnedMessage(eq(CHAT.longValue()), eq("text"), any(), any());
    }

    @Test
    void sameLanguageIsSavedWithoutMenu() {
        // Язык по умолчанию RU и выбор RU — выбор запоминаем, а клавиатура и так на русском
        when(texts.languageOf(USER)).thenReturn(AppLanguage.RU);

        switcher.switchLanguage(CHAT, USER, AppLanguage.RU);

        verify(texts).setLanguage(USER, AppLanguage.RU);
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void miniAppChangeSendsMenuToBotChat() {
        when(texts.languageOf(USER)).thenReturn(AppLanguage.RU);
        TelegramUser telegramUser = new TelegramUser();
        telegramUser.setTelegramId(CHAT);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(telegramUser));

        switcher.switchLanguage(USER, AppLanguage.EN);

        verify(texts).setLanguage(USER, AppLanguage.EN);
        verify(bot).sendReturnedMessage(eq(CHAT.longValue()), eq("text"), any(), any());
    }

    @Test
    void miniAppChangeWithoutBotChatOnlySaves() {
        when(texts.languageOf(USER)).thenReturn(AppLanguage.RU);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.empty());

        switcher.switchLanguage(USER, AppLanguage.EN);

        verify(texts).setLanguage(USER, AppLanguage.EN);
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }
}
