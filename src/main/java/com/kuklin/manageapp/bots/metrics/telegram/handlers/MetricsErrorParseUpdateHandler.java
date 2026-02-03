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

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsErrorParseUpdateHandler implements MetricsUpdateHandler {

    private final MetricsTelegramBot metricsTelegramBot;
    private static final Long ADMIN_TG_ID = 425120436L;
    private static final Long ADMIN_TG_ID_2 = 420478432L;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String message = update.getMessage().getText().split(TelegramBot.DEFAULT_DELIMETER)[1];

        log.info("Error bot processing received message");
        if (message.equals("1")) {
            log.error("test error message");
        } else if (message.equals("2")) {
            log.error("test error message {}", "test");
        } else if (message.equals("3")) {
            log.error("test error message {}", new Exception("test exception"));
        } else if (message.equals("4")) {
            log.error("test error message {} {} {}", "test", "longer", "123123");
        } else if (message.equals("5")) {
            log.error("test error message {}", new Exception("test exception", new IOException("test exception", new RuntimeException("test exception"))));
        }
    }

    public void sendErrorMessageToAdmin(ILoggingEvent event) {
        new Thread(() -> {
            try {
                sendErrorMessage(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sendErrorMessage(ILoggingEvent event) throws TelegramApiException {
        String message = event.getFormattedMessage();

        IThrowableProxy throwableProxy = event.getThrowableProxy();
        String trace = null;
        if (throwableProxy != null) {
            trace = throwableProxyToString(throwableProxy);
        }

        StringBuilder sb = new StringBuilder();
        if (trace != null) {
            sb.append("<b>Trace</b>: ").append(trace).append("\n");
        }
        sb.append("<b>Message</b>: ").append(message).append("\n");

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < sb.toString().length(); i += 4000) {
            chunks.add(sb.substring(i, Math.min(sb.toString().length(), i + 4000)));
        }

        for (String chunk : chunks) {
            try {
                metricsTelegramBot.execute(
                        SendMessage.builder()
                                .chatId(ADMIN_TG_ID)
                                .text(chunk)
                                .parseMode(ParseMode.HTML)
                                .build()
                );

                metricsTelegramBot.execute(
                        SendMessage.builder()
                                .chatId(ADMIN_TG_ID_2)
                                .text(chunk)
                                .parseMode(ParseMode.HTML)
                                .build()
                );
            } catch (Exception ex) {
                metricsTelegramBot.execute(
                        SendMessage.builder()
                                .chatId(ADMIN_TG_ID)
                                .text(chunk)
                                .build()
                );

                metricsTelegramBot.execute(
                        SendMessage.builder()
                                .chatId(ADMIN_TG_ID_2)
                                .text(chunk)
                                .build()
                );
            }
        }
    }

    private String throwableProxyToString(IThrowableProxy throwableProxy) {
        StringBuilder sb = new StringBuilder();
        while (throwableProxy != null) {
            sb.append(throwableProxy.getClassName()).append(": ")
                    .append(throwableProxy.getMessage()).append("\n");
            StackTraceElementProxy[] stackTrace = throwableProxy.getStackTraceElementProxyArray();
            for (StackTraceElementProxy element : stackTrace) {
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
