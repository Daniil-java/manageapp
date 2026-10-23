package com.kuklin.manageapp.common.library.tgmodels;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.InaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramBotCallbackMessageTest {

    @Test
    void accessibleMessageIsReturnedAsIs() {
        Message message = new Message();
        message.setMessageId(5);
        CallbackQuery query = new CallbackQuery();
        query.setMessage(message);

        assertThat(TelegramBot.getCallbackMessage(query)).isSameAs(message);
    }

    @Test
    void inaccessibleMessageKeepsChatAndId() {
        Chat chat = new Chat();
        chat.setId(42L);
        CallbackQuery query = new CallbackQuery();
        query.setMessage(new InaccessibleMessage(chat, 7, 0));

        Message message = TelegramBot.getCallbackMessage(query);

        assertThat(message.getChatId()).isEqualTo(42L);
        assertThat(message.getMessageId()).isEqualTo(7);
        assertThat(message.getText()).isNull();
    }

    @Test
    void noMessage() {
        assertThat(TelegramBot.getCallbackMessage(new CallbackQuery())).isNull();
    }
}
