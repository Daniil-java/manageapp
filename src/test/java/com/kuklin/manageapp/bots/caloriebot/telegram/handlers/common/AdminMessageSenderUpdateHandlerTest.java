package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminMessageSenderUpdateHandlerTest {
    private static final long CHAT_ID = 100L;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final AdminMessageSenderUpdateHandler handler = new AdminMessageSenderUpdateHandler(
            bot, telegramUserService, mock(CalorieMenuButtons.class), mock(CalorieTexts.class));

    @Test
    void blockedUserIsDeactivated() throws Exception {
        TelegramApiRequestException error = apiError(403, null);
        when(bot.execute(any(SendMessage.class))).thenThrow(error);

        assertThat(handler.send(CHAT_ID, "hi", null)).isEqualTo(AdminMessageSenderUpdateHandler.SendResult.BLOCKED);
        verify(telegramUserService).deactivateUser(CHAT_ID, BotIdentifier.CALORIE_BOT);
    }

    @Test
    void rateLimitIsRetried() throws Exception {
        TelegramApiRequestException error = apiError(429, 0);
        when(bot.execute(any(SendMessage.class))).thenThrow(error).thenReturn(new Message());

        assertThat(handler.send(CHAT_ID, "hi", null)).isEqualTo(AdminMessageSenderUpdateHandler.SendResult.SENT);
        verify(bot, times(2)).execute(any(SendMessage.class));
    }

    @Test
    void brokenHtmlIsResentAsPlainText() throws Exception {
        TelegramApiRequestException error = apiError(400, null);
        when(bot.execute(any(SendMessage.class))).thenThrow(error).thenReturn(new Message());

        assertThat(handler.send(CHAT_ID, "<b>hi", null)).isEqualTo(AdminMessageSenderUpdateHandler.SendResult.SENT);
        verify(bot, times(2)).execute(any(SendMessage.class));
    }

    private static TelegramApiRequestException apiError(int code, Integer retryAfter) {
        ResponseParameters parameters = new ResponseParameters();
        parameters.setRetryAfter(retryAfter);
        TelegramApiRequestException e = mock(TelegramApiRequestException.class);
        when(e.getErrorCode()).thenReturn(code);
        when(e.getParameters()).thenReturn(parameters);
        return e;
    }
}
