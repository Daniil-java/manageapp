package com.kuklin.manageapp.common.library.tgmodels;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Пул апдейтов переполнен (TaskRejectedException): пользователь получает «бот перегружен» и не остаётся
 * под замком inProcess, а оплата не теряется — обрабатывается сразу в потоке приёма.
 */
class TelegramBotOverloadTest {
    private static final long USER_ID = 1L;

    private static final class TestBot extends TelegramBot {
        final List<String> sent = new ArrayList<>();

        TestBot() {
            super("test-token");
        }

        @Override
        public Message sendReturnedMessage(long chatId, String text) {
            sent.add(chatId + ": " + text);
            return null;
        }

        @Override
        protected String getOverloadedText(Update update) {
            return "overloaded";
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

    private final AsyncService fullExecutor = mock(AsyncService.class);

    TelegramBotOverloadTest() {
        doThrow(new TaskRejectedException("queue is full")).when(fullExecutor).executeAsyncCustom(any(), any(), any());
    }

    @Test
    void rejectedMessageGetsOverloadedReplyAndReleasesLock() {
        TestBot bot = new TestBot();
        AtomicInteger handled = new AtomicInteger();

        assertThat(bot.doAsync(fullExecutor, message(), u -> handled.incrementAndGet())).isTrue();

        assertThat(bot.sent).containsExactly(USER_ID + ": overloaded");
        assertThat(handled).hasValue(0);
        // Замок снят: следующее сообщение не получит «предыдущее ещё обрабатывается»
        assertThat(bot.doAsync(mock(AsyncService.class), message(), u -> { })).isTrue();
    }

    @Test
    void rejectedPaymentIsHandledSynchronously() {
        TestBot bot = new TestBot();
        AtomicInteger handled = new AtomicInteger();

        assertThat(bot.doAsync(fullExecutor, preCheckout(), u -> handled.incrementAndGet())).isTrue();

        assertThat(handled).hasValue(1);
        assertThat(bot.sent).isEmpty();
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        return user;
    }

    private static Update message() {
        Chat chat = new Chat();
        chat.setId(USER_ID);
        Message message = new Message();
        message.setFrom(user());
        message.setChat(chat);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static Update preCheckout() {
        PreCheckoutQuery query = new PreCheckoutQuery();
        query.setFrom(user());
        Update update = new Update();
        update.setPreCheckoutQuery(query);
        return update;
    }
}
