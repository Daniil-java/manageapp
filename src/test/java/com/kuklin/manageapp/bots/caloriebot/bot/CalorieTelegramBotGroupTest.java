package com.kuklin.manageapp.bots.caloriebot.bot;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;

import static org.assertj.core.api.Assertions.assertThat;

/** Бот отвечает только в личке: сообщение из группы не должно уйти в фасад как еда. */
class CalorieTelegramBotGroupTest {

    private static Update buildMessageUpdate(long chatId, String type) {
        Message message = new Message();
        message.setChat(new Chat(chatId, type));
        message.setText("привет");
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @Test
    void privateChatIsNotGroup() {
        assertThat(CalorieTelegramBot.isGroupUpdate(buildMessageUpdate(555L, "private"))).isFalse();
    }

    @Test
    void supergroupIsGroup() {
        assertThat(CalorieTelegramBot.isGroupUpdate(buildMessageUpdate(-1001234567890L, "supergroup"))).isTrue();
    }

    @Test
    void preCheckoutWithoutChatIsNotGroup() {
        Update update = new Update();
        update.setPreCheckoutQuery(new PreCheckoutQuery());
        assertThat(CalorieTelegramBot.isGroupUpdate(update)).isFalse();
    }
}
