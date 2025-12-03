package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiLogService;
import com.kuklin.manageapp.bots.metrics.telegram.MetricsTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class MetricsLogUpdateHandler implements MetricsUpdateHandler {
    private final MetricsTelegramBot telegramBot;
    private final MetricsAiLogService metricsAiLogService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        send(chatId);
    }

    @Scheduled(cron = "0 50 23 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void sendLog() {
        Long adminId = 425120436L;
        send(adminId);
    }

    private void send(Long chatId) {
        MetricsAiLog log = metricsAiLogService.getTodayLog();

        telegramBot.sendReturnedMessage(
                chatId,
                log.getStringForTelegram()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.METRICS_GET.getCommandText();
    }
}
