package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history.report;

import com.kuklin.manageapp.bots.caloriebot.components.services.AiRateLimiter;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReportService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.FeatureLimitNotifier;
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
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;


/**
 * Обработчик команд формирования отчетов по калориям.
 * Поддерживает вывод меню выбора и генерацию PDF/AI отчетов.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieReportUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final ReportService reportService;
    private final CalorieWeightHistoryUpdateHandler calorieWeightHistoryUpdateHandler;
    private final FeatureLimitNotifier featureLimitNotifier;
    private final AiRateLimiter aiRateLimiter;
    private final CalorieTexts calorieTexts;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        } else if (update.hasMessage()) {
            Long chatId = update.getMessage().getChatId();
            Long appUserId = telegramUser.getAppUserId();
            calorieTelegramBot.sendReturnedMessage(chatId,
                    calorieTexts.get(appUserId, BotTextKeys.REPORT_CHOOSE), buildReportKeyboard(appUserId), null);
        }
    }

    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        // Возврат к основному меню отчетов
        if (data.equals(getHandlerListName())) {
            Long appUserId = telegramUser.getAppUserId();
            calorieTelegramBot.sendEditMessage(chatId,
                    calorieTexts.get(appUserId, BotTextKeys.REPORT_CHOOSE), messageId, buildReportKeyboard(appUserId));
            return;
        }

        try {
            // Извлекаем тип отчета из callback data (формат: COMMAND:TYPE)
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            if (parts.length < 2) return;

            ReportType reportType = ReportType.valueOf(parts[1]);

            // Отчёты по дню / неделе / месяцу обращаются к ИИ, отчёт по весу — нет
            if (reportType != ReportType.WEIGHT) {
                AiRateLimiter.Decision decision = aiRateLimiter.tryAcquire(telegramUser.getAppUserId());
                if (!decision.allowed()) {
                    featureLimitNotifier.sendAiRateLimited(chatId, telegramUser.getAppUserId(), decision);
                    return;
                }
            }

            calorieTelegramBot.sendChatActionTyping(chatId);
            // Обертка с индикацией загрузки
            executeWithAwait(chatId, telegramUser.getAppUserId(), () -> {
                switch (reportType) {
                    case DAY -> handleDayReport(chatId, telegramUser.getAppUserId());
                    case WEEK, MONTH -> handlePdfReport(chatId, telegramUser.getAppUserId(), reportType);
                    case WEIGHT -> calorieWeightHistoryUpdateHandler.handle(update, telegramUser);
                }
            });

        } catch (Exception e) {
            log.error("{} Callback error! Data: {}", calorieTelegramBot.getBotUsername(), data, e);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    calorieTexts.get(telegramUser.getAppUserId(), BotTextKeys.REPORT_DATA_ERROR));
        }
    }

    /**
     * Выполняет действие (action), предварительно отправив сообщение об ожидании.
     * После выполнения (успешного или нет) удаляет сообщение об ожидании.
     */
    private void executeWithAwait(Long chatId, Long appUserId, Runnable action) {
        Integer awaitMsgId = null;
        try {
            Message message = calorieTelegramBot.sendReturnedMessage(chatId, calorieTexts.get(appUserId, PdfTextKeys.GENERATING));
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
        try {
            String aiReport = reportService.getDayAiReport(appUserId).getOrThrow();
            calorieTelegramBot.sendReturnedMessage(chatId, aiReport);
        } catch (MissingFeatureException e) {
            featureLimitNotifier.sendLimitReached(chatId, appUserId, e.getRequiredFeature());
        }
    }

    private void handlePdfReport(Long chatId, Long appUserId, ReportType type) {
        try {
            byte[] report = (type == ReportType.WEEK
                    ? reportService.buildWeeklyPdfReportOrNull(appUserId)
                    : reportService.buildMonthlyPdfReportOrNull(appUserId)
            ).getOrThrow();
            sendPdfOrError(chatId, appUserId, report, type);
        } catch (MissingFeatureException e) {
            featureLimitNotifier.sendLimitReached(chatId, appUserId, e.getRequiredFeature());
        }
    }

    private void sendPdfOrError(Long chatId, Long appUserId, byte[] content, ReportType type) {
        if (content != null && content.length > 0) {
            calorieTelegramBot.sendDocument(chatId, content, type.fileName, calorieTexts.get(appUserId, type.captionKey));
        } else {
            calorieTelegramBot.sendReturnedMessage(chatId, calorieTexts.get(appUserId, PdfTextKeys.FAILED));
        }
    }

    @Getter
    @AllArgsConstructor
    public enum ReportType {
        DAY("day-report.pdf", PdfTextKeys.CAPTION_DAY, BotTextKeys.REPORT_BUTTON_DAY),
        WEEK("week-report.pdf", PdfTextKeys.CAPTION_WEEK, BotTextKeys.REPORT_BUTTON_WEEK),
        MONTH("month-report.pdf", PdfTextKeys.CAPTION_MONTH, BotTextKeys.REPORT_BUTTON_MONTH),
        WEIGHT("weight-report.pdf", PdfTextKeys.CAPTION_WEIGHT, BotTextKeys.REPORT_BUTTON_WEIGHT);

        private final String fileName;
        /** Подпись к файлу — ключ словаря pdf_*. */
        private final String captionKey;
        /** Подпись кнопки в меню аналитики — ключ словаря bot_*. */
        private final String buttonKey;
    }

    private InlineKeyboardMarkup buildReportKeyboard(Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        String base = getHandlerListName() + TelegramBot.DEFAULT_DELIMETER;

        for (ReportType type : ReportType.values()) {
            builder.row(TelegramKeyboard.button(calorieTexts.get(appUserId, type.getButtonKey()), base + type.name()));
        }

        builder.row(TelegramKeyboard.button(
                calorieTexts.get(appUserId, BotTextKeys.COMMON_BUTTON_CLOSE), Command.CALORIE_CLOSE.getCommandText()));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_REPORT.getCommandText();
    }
}