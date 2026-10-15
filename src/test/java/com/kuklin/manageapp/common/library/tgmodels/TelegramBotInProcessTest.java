package com.kuklin.manageapp.common.library.tgmodels;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Замок «одно сообщение пользователя за раз» (inProcess): add — в потоке приёма апдейтов,
 * remove — в @Async-потоке после обработки. Потерянный remove навсегда блокировал пользователя.
 * Саму гонку HashSet тестом не поймать (не воспроизводится даже на 200 000 пользователей) —
 * здесь только логика замка; потокобезопасность даёт ConcurrentHashMap.newKeySet().
 */
class TelegramBotInProcessTest {

    private static final class TestBot extends TelegramBot {
        TestBot() {
            super("test-token");
        }

        @Override
        public void handleUpdateDirectly(Update update) {
        }

        @Override
        public void onUpdateReceived(Update update) {
        }

        @Override
        public String getBotUsername() {
            return "test_bot";
        }

        @Override
        public BotIdentifier getBotIdentifier() {
            return BotIdentifier.CALORIE_BOT;
        }
    }

    private static Update messageFrom(long tgUserId) {
        User user = new User();
        user.setId(tgUserId);
        Message message = new Message();
        message.setFrom(user);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    // Обработка «повисла»: AsyncService-мок ничего не выполняет и замок не снимает
    private final AsyncService inFlight = mock(AsyncService.class);

    @Test
    void secondMessageWaitsUntilFirstDone() {
        TestBot bot = new TestBot();
        Update update = messageFrom(1L);

        assertThat(bot.doAsync(inFlight, update, u -> { })).isTrue();
        assertThat(bot.doAsync(inFlight, update, u -> { })).isFalse();

        bot.notifyAsyncDone(update);
        assertThat(bot.doAsync(inFlight, update, u -> { })).isTrue();
    }

    @Test
    void otherUsersAreNotBlocked() {
        TestBot bot = new TestBot();

        assertThat(bot.doAsync(inFlight, messageFrom(1L), u -> { })).isTrue();
        assertThat(bot.doAsync(inFlight, messageFrom(2L), u -> { })).isTrue();
    }
}
