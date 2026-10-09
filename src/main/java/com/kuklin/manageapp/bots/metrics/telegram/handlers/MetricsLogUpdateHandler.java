package com.kuklin.manageapp.bots.metrics.telegram.handlers;

import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.bots.metrics.configurations.MetricsBotKeyComponents;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
@BotScheduler(BotIdentifier.METRICS)
public class MetricsLogUpdateHandler implements MetricsUpdateHandler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MetricsTelegramBot telegramBot;
    private final MetricsAiLogService metricsAiLogService;
    private final MetricsAiInteractionRecordService metricsAiInteractionRecordService;
    private final MetricsBotKeyComponents metricsBotKeyComponents;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        send(chatId, LocalDate.now(ZONE));
    }

    @Scheduled(cron = "0 50 23 * * ?", zone = "Asia/Ho_Chi_Minh")
    public void sendLog() {
        LocalDate day = reportDay(ZonedDateTime.now(ZONE));
        for (Long id: metricsBotKeyComponents.getAdminIds()) {
            send(id, day);
        }
    }

    /**
     * День, за который вечерний отчёт (23:50). Если планировщик был занят и отчёт уходит уже после полуночи —
     * за вчера, а не за только начавшийся день (08.10 отчёт пришёл в 00:21 с нулями).
     */
    static LocalDate reportDay(ZonedDateTime sendingAt) {
        LocalDate day = sendingAt.toLocalDate();
        return sendingAt.toLocalTime().isBefore(LocalTime.NOON) ? day.minusDays(1) : day;
    }

    private void send(Long chatId, LocalDate day) {
        MetricsAiLog log = metricsAiLogService.getLog(day);
        String aiInteractionRecords = metricsAiInteractionRecordService.buildStatistics(day);

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
