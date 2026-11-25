package com.kuklin.manageapp.common.logging;

import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Контекст логов апдейта: «[бот tg=… upd=…]» во время обработки, после — убран, даже если обработка упала.
 */
class LogContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void contextIsSetDuringHandlingAndRemovedAfter() {
        AtomicReference<String> during = new AtomicReference<>();

        LogContext.runWithUpdate(BotIdentifier.CALORIE_BOT, messageUpdate(), () -> during.set(MDC.get(LogContext.KEY)));

        assertThat(during.get()).isEqualTo("[CALORIE_BOT tg=123 upd=456]");
        assertThat(MDC.get(LogContext.KEY)).isNull();
    }

    @Test
    void contextIsRemovedWhenHandlingFails() {
        assertThatThrownBy(() -> LogContext.runWithUpdate(BotIdentifier.CALORIE_BOT, messageUpdate(), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(LogContext.KEY)).isNull();
    }

    @Test
    void callbackUserIsTaken() {
        Update update = new Update();
        update.setUpdateId(7);
        CallbackQuery query = new CallbackQuery();
        query.setFrom(user(99L));
        update.setCallbackQuery(query);

        assertThat(LogContext.buildUpdateContext(BotIdentifier.CALORIE_BOT, update))
                .isEqualTo("[CALORIE_BOT tg=99 upd=7]");
    }

    private static Update messageUpdate() {
        Update update = new Update();
        update.setUpdateId(456);
        Message message = new Message();
        message.setFrom(user(123L));
        update.setMessage(message);
        return update;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
