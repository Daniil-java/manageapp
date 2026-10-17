package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history.report;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.weight.CalorieWeightHistoryUpdateHandler;
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
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Обработчик команд формирования отчетов по калориям.
 * Поддерживает вывод меню выбора и генерацию PDF/AI отчетов.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CalorieReportUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final ReportService reportService;
    private final CalorieWeightHistoryUpdateHandler calorieWeightHistoryUpdateHandler;

    private static final String MSG_CHOOSE_REPORT = "Выберите тип аналитики";
    private static final String CLB_DATA_ERROR = "Ошибка данных! Попробуйте повторить операцию позже!";
    private static final String DOC_ERROR = "Не получилось сгенерировать отчет! Попробуйте еще раз";
    private static final String AWAIT_MSG = "Генерирую документ...";
    private static final String ACCESS_DENIED_MSG = "Доступ ограничен!";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            calorieTelegramBot.sendReturnedMessage(chatId, MSG_CHOOSE_REPORT, buildReportKeyboard(), null);
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        // Возврат к основному меню отчетов
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(chatId, MSG_CHOOSE_REPORT, messageId, buildReportKeyboard());
            return;
        }

        try {
            // Извлекаем тип отчета из callback data (формат: COMMAND:TYPE)
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            if (parts.length < 2) return;

            ReportType reportType = ReportType.valueOf(parts[1]);

            calorieTelegramBot.sendChatActionTyping(chatId);
            // Обертка с индикацией загрузки
            executeWithAwait(chatId, () -> {
                switch (reportType) {
                    case DAY -> handleDayReport(chatId, telegramUser.getAppUserId());
                    case WEEK -> handleWeeklyDeepReport(chatId, telegramUser.getAppUserId());
                    case MONTH -> handleStandardPdfReport(chatId, telegramUser.getAppUserId(), reportType);
                    case WEIGHT -> calorieWeightHistoryUpdateHandler.handle(update, telegramUser);
                }
            });

        } catch (Exception e) {
            log.error("{} Callback error! Data: {}", calorieTelegramBot.getBotUsername(), data, e);
            calorieTelegramBot.sendReturnedMessage(chatId, CLB_DATA_ERROR);
        }
    }

    /**
     * Выполняет действие (action), предварительно отправив сообщение об ожидании.
     * После выполнения (успешного или нет) удаляет сообщение об ожидании.
     */
    private void executeWithAwait(Long chatId, Runnable action) {
        Integer awaitMsgId = null;
        try {
            Message message = calorieTelegramBot.sendReturnedMessage(chatId, AWAIT_MSG);
            if (message != null) {
                awaitMsgId = message.getMessageId();
            }

            // Выполнение бизнес-логики (генерация PDF или AI запрос)
            action.run();
        } finally {
            // Блок finally гарантирует, что "Генерирую..." удалится даже при Exception в action.run()
            if (awaitMsgId != null) {
                calorieTelegramBot.sendDeleteMessage(chatId, awaitMsgId);
            }
        }
    }

    private void handleDayReport(Long chatId, Long appUserId) {
        String aiReport = null;
        try {
            aiReport = reportService.getDayAiReport(appUserId).getOrThrow();
        } catch (MissingFeatureException e) {
            aiReport = ACCESS_DENIED_MSG;
        }
        calorieTelegramBot.sendReturnedMessage(chatId, aiReport);
    }

    private void handleWeeklyDeepReport(Long chatId, Long appUserId) {
        try {
            Instant now = Instant.now();
            byte[] report = reportService.buildWeeklyDeepPdfReportOrNull(
                    ReportType.WEEK.getFrom(now),
                    now,
                    appUserId
            ).getOrThrow();
            sendPdfOrError(chatId, report, ReportType.WEEK);
        } catch (MissingFeatureException e) {
            calorieTelegramBot.sendReturnedMessage(chatId, ACCESS_DENIED_MSG);
        }

    }

    private void handleStandardPdfReport(Long chatId, Long appUserId, ReportType type) {
        try {
            Instant now = Instant.now();
            byte[] report = reportService.buildPdfReportOrNull(
                    type.getFrom(now),
                    now,
                    appUserId
            ).getOrThrow();
            sendPdfOrError(chatId, report, type);
        } catch (MissingFeatureException e) {
            calorieTelegramBot.sendReturnedMessage(chatId, ACCESS_DENIED_MSG);
        }
    }

    private void sendPdfOrError(Long chatId, byte[] content, ReportType type) {
        if (content != null && content.length > 0) {
            calorieTelegramBot.sendDocument(chatId, content, type.fileName, type.caption);
        } else {
            calorieTelegramBot.sendReturnedMessage(chatId, DOC_ERROR);
        }
    }

    @Getter
    @AllArgsConstructor
    public enum ReportType {
        DAY("day-report.pdf", "ДНЕВНОЙ ОТЧЕТ", 1l, ChronoUnit.DAYS),
        WEEK("week-report.pdf", "НЕДЕЛЬНЫЙ ОТЧЕТ", 7l, ChronoUnit.DAYS),
        MONTH("month-report.pdf", "МЕСЯЧНЫЙ ОТЧЕТ", 30l, ChronoUnit.DAYS),
        WEIGHT("weight-report.pdf", "ОТЧЕТ ПО ВЕСУ", null, null);

        private final String fileName;
        private final String caption;
        private final Long amount;
        private final ChronoUnit unit;

        public Instant getFrom(Instant now) {
            return now.minus(amount, unit);
        }
    }

    private InlineKeyboardMarkup buildReportKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        String base = getHandlerListName() + TelegramBot.DEFAULT_DELIMETER;

        for (ReportType type : ReportType.values()) {
            String label = type == ReportType.DAY ? "Дневной" :
                    type == ReportType.WEEK ? "Недельный" :
                            type == ReportType.MONTH ? "Месячный" : "Отчет по весу";
            builder.row(TelegramKeyboard.button(label, base + type.name()));
        }

        builder.row(TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText()));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_REPORT.getCommandText();
    }
}