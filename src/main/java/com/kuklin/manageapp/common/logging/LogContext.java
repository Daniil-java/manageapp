package com.kuklin.manageapp.common.logging;

import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.slf4j.MDC;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Контекст запроса в логах (MDC): каждая строка лога, написанная при обработке апдейта,
 * получает «[CALORIE_BOT tg=123 upd=456]» — в логах Railway можно отфильтровать историю одного пользователя.
 * <p>
 * MDC живёт в ThreadLocal потока: в начале обработки кладём, в конце обязательно убираем,
 * иначе поток пула унесёт чужой контекст в следующую задачу. В другие пулы контекст переносит {@link MdcTaskDecorator}.
 * Шаблон лога — logging.pattern.level в application.yaml.
 */
public final class LogContext {
    public static final String KEY = "ctx";

    private LogContext() {
    }

    /** Выполнить обработку апдейта с контекстом в логах; после — вернуть контекст потока как был. */
    public static void runWithUpdate(BotIdentifier botIdentifier, Update update, Runnable action) {
        String previous = MDC.get(KEY);
        MDC.put(KEY, buildUpdateContext(botIdentifier, update));
        try {
            action.run();
        } finally {
            if (previous == null) MDC.remove(KEY);
            else MDC.put(KEY, previous);
        }
    }

    static String buildUpdateContext(BotIdentifier botIdentifier, Update update) {
        StringBuilder sb = new StringBuilder("[").append(botIdentifier);
        User user = findUser(update);
        if (user != null) sb.append(" tg=").append(user.getId());
        if (update.getUpdateId() != null) sb.append(" upd=").append(update.getUpdateId());
        return sb.append(']').toString();
    }

    private static User findUser(Update update) {
        if (update.hasMessage()) return update.getMessage().getFrom();
        if (update.hasEditedMessage()) return update.getEditedMessage().getFrom();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getFrom();
        if (update.hasPreCheckoutQuery()) return update.getPreCheckoutQuery().getFrom();
        if (update.hasMyChatMember()) return update.getMyChatMember().getFrom();
        return null;
    }
}
