package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import com.kuklin.manageapp.bots.metrics.telegram.MetricsTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsErrorParseUpdateHandler implements MetricsUpdateHandler {

    private final MetricsTelegramBot metricsTelegramBot;

    // Список администраторов для рассылки
    private static final List<Long> ADMIN_IDS = List.of(425120436L, 420478432L);
    private static final int MAX_MESSAGE_LENGTH = 4000;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String[] parts = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < 2) return;

        String message = parts[1];
        log.info("Error bot processing received message: {}", message);

        // Удобный switch для тестовых вызовов
        switch (message) {
            case "1" -> log.error("test error message");
            case "2" -> log.error("test error message {}", "test");
            case "3" -> log.error("test error message {}", new Exception("test exception"));
            case "4" -> log.error("test error message {} {} {}", "test", "longer", "123123");
            case "5" -> log.error("test error message {}", new Exception("test exception",
                    new IOException("inner io", new RuntimeException("inner runtime"))));
            default -> log.warn("Unknown test error code: {}", message);
        }
    }

    public void sendErrorMessageToAdmin(ILoggingEvent event) {
        // Используем CompletableFuture (стандарт Spring/Java) вместо ручного создания Thread
        CompletableFuture.runAsync(() -> {
            try {
                sendErrorMessage(event);
            } catch (Exception e) {
                log.error("Критическая ошибка при отправке лога в Telegram", e);
            }
        });
    }

    public void sendErrorMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();

        // 1. Фильтр: Конфликт сессий (409)
        if (message != null && message.contains("terminated by other getUpdates request")) {
            broadcastToAdmins("⚠️ <b>Telegram 409 Conflict</b>: Похоже, запущено два инстанса бота.", ParseMode.HTML);
            return;
        }

        // 2. Фильтр: Сетевые проблемы (Unreachable / No Response)
        if (handleNetworkErrors(event)) {
            return;
        }

        // 3. Общий случай: Формируем полный лог со стектрейсом
        StringBuilder sb = new StringBuilder();
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy != null) {
            sb.append("<b>Trace</b>: ").append(throwableProxyToString(throwableProxy)).append("\n");
        }
        sb.append("<b>Message</b>: ").append(message).append("\n");

        // Разбиваем длинный текст и отправляем
        splitIntoChunks(sb.toString()).forEach(chunk -> broadcastToAdmins(chunk, ParseMode.HTML));
    }

    /**
     * Распознает сетевые ошибки и отправляет короткие алерты
     */
    private boolean handleNetworkErrors(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        IThrowableProxy tp = event.getThrowableProxy();
        String exceptionData = (tp != null) ? (tp.getClassName() + " " + tp.getMessage()) : "";

        // Создаем строку для поиска в нижнем регистре
        String searchTarget = ((message != null ? message : "") + " " + exceptionData).toLowerCase();

        // 1. Кейс: Сеть недоступна
        if (searchTarget.contains("network is unreachable") || searchTarget.contains("socketexception")) {
            broadcastToAdmins("🌐 <b>CRITICAL Network</b>: Сеть недоступна (Network unreachable). Проверьте хост/DNS.", ParseMode.HTML);
            return true;
        }

        // 2. Кейс: Telegram молчит
        if (searchTarget.contains("nohttpresponseexception") || searchTarget.contains("failed to respond")) {
            broadcastToAdmins("🔌 <b>WARNING Network</b>: api.telegram.org не ответил. Возможен конфликт сессий.", ParseMode.HTML);
            return true;
        }

        // 3. НОВЫЙ IF: Кейс: EditMessageText [400] Not Modified
        if (searchTarget.contains("message is not modified")) {
            StringBuilder sb = new StringBuilder();
            sb.append("🖱️ <b>INFO 400 UI</b>: Сообщение не изменено.\n\n");

            // Добавляем инфу из лога, чтобы видеть, где именно упало
            sb.append("<b>Log Message:</b> ").append(message).append("\n");
            if (tp != null) {
                sb.append("<b>Exception:</b> ").append(tp.getClassName()).append(": ").append(tp.getMessage()).append("\n");
            }

            sb.append("\n<b>Анализ:</b>\n");
            sb.append("• Если пришло сразу несколько таких логов — юзер <b>дважды кликнул</b> по кнопке.\n");
            sb.append("• Если лог один — проверь код хендлера, он отправляет <b>старый текст/разметку</b> без изменений.");

            broadcastToAdmins(sb.toString(), ParseMode.HTML);
            return true;
        }

        return false;
    }

    /**
     * Отправка сообщения всем админам из списка
     */
    private void broadcastToAdmins(String text, String parseMode) {
        for (Long chatId : ADMIN_IDS) {
            try {
                executeSendMessage(chatId, text, parseMode);
            } catch (Exception ex) {
                // Если ошибка в HTML-тегах, пробуем отправить голый текст
                if (parseMode != null) {
                    try {
                        executeSendMessage(chatId, text, null);
                    } catch (Exception ignore) {
                        log.info("Failed to send message to admin {}", chatId);
                    }
                }
            }
        }
    }

    private void executeSendMessage(Long chatId, String text, String parseMode) throws TelegramApiException {
        metricsTelegramBot.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .parseMode(parseMode)
                        .build()
        );
    }

    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += MAX_MESSAGE_LENGTH) {
            chunks.add(text.substring(i, Math.min(text.length(), i + MAX_MESSAGE_LENGTH)));
        }
        return chunks;
    }

    private String throwableProxyToString(IThrowableProxy throwableProxy) {
        StringBuilder sb = new StringBuilder();
        while (throwableProxy != null) {
            sb.append(throwableProxy.getClassName()).append(": ")
                    .append(throwableProxy.getMessage()).append("\n");

            for (StackTraceElementProxy element : throwableProxy.getStackTraceElementProxyArray()) {
                sb.append(element.toString()).append("\n");
            }
            throwableProxy = throwableProxy.getCause();
            if (throwableProxy != null) {
                sb.append("Caused by: ");
            }
        }
        return sb.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.METRICS_GET.getCommandText();
    }
}