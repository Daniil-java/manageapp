package com.kuklin.manageapp.bots.caloriebot.telegram.history.report;

import com.kuklin.manageapp.bots.caloriebot.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.history.TodayUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/*
Отдает сообщение со списком возможных отчетов
Принимает колбэк и сообщение
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CalorieReportUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final ReportService reportService;
    private final TodayUpdateHandler todayUpdateHandler;
    private static final String MSG =
            """
                    Выберите тип отчета
                    """;
    private static final String CLB_DATA_ERROR = "Ошибка данных! Попробуйте повторить операцию позже!";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {

        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else if (update.hasMessage()) {
            //Отправляем новое сообщение
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    MSG,
                    buildReportKeyboard(),
                    null
            );
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    MSG,
                    update.getCallbackQuery().getMessage().getMessageId(),
                    buildReportKeyboard()
            );
            return;
        }

        try {
            // Извлекаем тип отчета из callback data
            ReportType reportType = ReportType.valueOf(data.split(TelegramBot.DEFAULT_DELIMETER)[1]);

            if (reportType.equals(ReportType.DAY)) {
                processDayReportType(update, telegramUser);
                return;
            } else if (reportType.equals(ReportType.WEEK)) {
                processWeekReportType(update, telegramUser);
                return;
            }
            Instant now = Instant.now();
            // Используем логику из Enum
            byte[] pdfReport = reportService.buildPdfReportOrNull(
                    reportType.getFrom(now),
                    now,
                    telegramUser.getTelegramId()
            );

            if (pdfReport != null && pdfReport.length > 0) {
                calorieTelegramBot.sendDocument(
                        chatId,
                        pdfReport,
                        reportType.getFileName(),
                        reportType.getCaption()
                );
            }

        } catch (Exception e) {
            log.error("{} CallbackData error! Data: {}", calorieTelegramBot.getBotUsername(), data, e);
            calorieTelegramBot.sendReturnedMessage(chatId, CLB_DATA_ERROR);
        }
    }

    private void processWeekReportType(Update update, TelegramUser telegramUser) {
        byte[] weeklyReport = reportService
                .buildWeeklyDeepPdfReportOrNull(
                        Instant.now().minus(7, ChronoUnit.DAYS),
                        Instant.now(),
                        telegramUser.getTelegramId()
                );
        calorieTelegramBot.sendDocument(
                update.getCallbackQuery().getMessage().getChatId(),
                weeklyReport,
                ReportType.WEEK.fileName,
                ReportType.WEEK.getCaption()
        );
    }

    private void processDayReportType(Update update, TelegramUser telegramUser) {
        todayUpdateHandler.sendTodayMessage(telegramUser.getTelegramId());
        String aiReport = reportService.getDayAiReport(telegramUser.getTelegramId());
        calorieTelegramBot.sendReturnedMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                aiReport
        );
    }

    @Getter
    @AllArgsConstructor
    public enum ReportType {
        DAY("day-report.pdf", "ДНЕВНОЙ ОТЧЕТ", 1, ChronoUnit.DAYS),
        WEEK("week-report.pdf", "НЕДЕЛЬНЫЙ ОТЧЕТ", 7, ChronoUnit.DAYS),
        MONTH("month-report.pdf", "МЕСЯЧНЫЙ ОТЧЕТ", 30, ChronoUnit.DAYS);

        private final String fileName;
        private final String caption;
        private final long amount;
        private final ChronoUnit unit;

        public Instant getFrom(Instant now) {
            return now.minus(amount, unit);
        }
    }

    private InlineKeyboardMarkup buildReportKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        String base = getHandlerListName() + TelegramBot.DEFAULT_DELIMETER;

        builder
                .row(
                        TelegramKeyboard.button("Дневной", base + ReportType.DAY)
                ).row(
                        TelegramKeyboard.button("Недельный", base + ReportType.WEEK)
                ).row(
                        TelegramKeyboard.button("Месячный", base + ReportType.MONTH)
                ).row(
                        TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText())
                );

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_REPORT.getCommandText();
    }
}
