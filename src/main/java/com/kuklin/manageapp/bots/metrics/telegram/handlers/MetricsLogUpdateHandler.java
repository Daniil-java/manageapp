package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import com.kuklin.manageapp.bots.metrics.configurations.MetricsBotKeyComponents;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiLog;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiInteractionRecordService;
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
    private final MetricsAiInteractionRecordService metricsAiInteractionRecordService;
    private final MetricsBotKeyComponents metricsBotKeyComponents;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        send(chatId);
    }

    @Scheduled(cron = "0 50 23 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void sendLog() {
        for (Long id: metricsBotKeyComponents.getAdminIds()) {
            send(id);
        }
    }

    private void send(Long chatId) {
        MetricsAiLog log = metricsAiLogService.getTodayLog();
        String aiInteractionRecords = metricsAiInteractionRecordService.buildStatisticsForToday();

        telegramBot.sendReturnedMessage(
                chatId,
                log.getStringForTelegram()
        );

        telegramBot.sendReturnedMessage(
                chatId,
                aiInteractionRecords
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.METRICS_GET.getCommandText();
    }
}
