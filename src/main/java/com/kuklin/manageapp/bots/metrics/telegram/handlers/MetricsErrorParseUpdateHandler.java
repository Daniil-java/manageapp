package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.kuklin.manageapp.bots.metrics.configurations.MetricsBotKeyComponents;
import com.kuklin.manageapp.bots.metrics.services.ErrorReportFormatter;
import com.kuklin.manageapp.bots.metrics.services.ErrorStormGuard;
import com.kuklin.manageapp.bots.metrics.telegram.MetricsTelegramBot;
import com.kuklin.manageapp.common.configurations.EnabledBots;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Ошибки приложения (log.error из любого места, через LoggingConfig) — админам метрикс-бота (METRICS_ADMIN_IDS).
 * Сообщение — короткая выжимка (ErrorReportFormatter), одинаковые ошибки не чаще раза в 2 минуты
 * со сводкой повторов (ErrorStormGuard). Выключен METRICS (bots.disabled) — ошибки не отправляются.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsErrorParseUpdateHandler implements MetricsUpdateHandler {

    private final MetricsTelegramBot metricsTelegramBot;
    private final MetricsBotKeyComponents metricsBotKeyComponents;
    private final ErrorReportFormatter errorReportFormatter;
    private final ErrorStormGuard errorStormGuard;
    private final EnabledBots enabledBots;

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

    /**
     * Вызывается приёмником логов на каждый ERROR. Текст собирается сразу, в потоке ошибки:
     * место вызова log.error (callerData) logback вычисляет по текущему стеку. Отправка — асинхронно.
     */
    public void sendErrorMessageToAdmin(ILoggingEvent event) {
        if (!enabledBots.isEnabled(BotIdentifier.METRICS)) return;
        // Свои ошибки отправки не пересылаем — иначе петля
        if (getClass().getName().equals(event.getLoggerName())) return;

        String signature;
        String title;
        String text;
        try {
            String known = knownErrorMessage(event);
            if (known != null) {
                signature = known;
                title = known;
                text = known;
            } else {
                signature = errorReportFormatter.signature(event);
                title = errorReportFormatter.title(event);
                text = errorReportFormatter.format(event);
            }
        } catch (Exception e) {
            log.warn("Failed to format error for admins", e);
            return;
        }

        if (!errorStormGuard.tryAcquire(signature, title, Instant.now())) return;
        CompletableFuture.runAsync(() -> broadcastToAdmins(text));
    }

    /** Сводки по ошибкам, которые повторялись в последние 2 минуты. */
    @Scheduled(fixedDelay = 20_000)
    public void sendRepeatSummaries() {
        if (!enabledBots.isEnabled(BotIdentifier.METRICS)) return;
        for (ErrorStormGuard.Summary summary : errorStormGuard.flush(Instant.now())) {
            broadcastToAdmins("🔁 <b>Повторилась ещё ×" + summary.repeats() + " за 2 мин</b>\n"
                    + escape(summary.title()));
        }
    }

    /**
     * Известные шумные ошибки — короткий текст вместо разбора стектрейса.
     * Текст же служит ключом для защиты от шквала. null — обычная ошибка.
     */
    private String knownErrorMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        IThrowableProxy tp = event.getThrowableProxy();
        String exceptionData = (tp != null) ? (tp.getClassName() + " " + tp.getMessage()) : "";
        String searchTarget = ((message != null ? message : "") + " " + exceptionData).toLowerCase();

        if (searchTarget.contains("terminated by other getupdates request")) {
            return "⚠️ <b>Telegram 409 Conflict</b>: Похоже, запущено два инстанса бота.";
        }
        if (searchTarget.contains("network is unreachable") || searchTarget.contains("socketexception")) {
            return "🌐 <b>CRITICAL Network</b>: Сеть недоступна (Network unreachable). Проверьте хост/DNS.";
        }
        if (searchTarget.contains("nohttpresponseexception") || searchTarget.contains("failed to respond")) {
            return "🔌 <b>WARNING Network</b>: api.telegram.org не ответил. Возможен конфликт сессий.";
        }
        if (searchTarget.contains("message is not modified")) {
            return "🖱️ <b>INFO 400 UI</b>: Сообщение не изменено — " + escape(errorReportFormatter.title(event)) + "\n"
                    + "• Несколько таких подряд — юзер <b>дважды кликнул</b> по кнопке.\n"
                    + "• Одно — хендлер отправляет <b>старый текст/разметку</b> без изменений.";
        }
        return null;
    }

    private void broadcastToAdmins(String text) {
        String limited = text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH) + "…";
        for (Long chatId : metricsBotKeyComponents.getAdminIds()) {
            try {
                executeSendMessage(chatId, limited, ParseMode.HTML);
            } catch (Exception ex) {
                // Если ошибка в HTML-тегах, пробуем отправить голый текст
                try {
                    executeSendMessage(chatId, limited, null);
                } catch (Exception ignore) {
                    log.info("Failed to send message to admin {}", chatId);
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
                        .disableWebPagePreview(true)
                        .build()
        );
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String getHandlerListName() {
        return Command.METRICS_GET.getCommandText();
    }
}
