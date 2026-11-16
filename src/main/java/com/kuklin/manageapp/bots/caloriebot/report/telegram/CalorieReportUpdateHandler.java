package com.kuklin.manageapp.bots.caloriebot.report.telegram;

import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import com.kuklin.manageapp.bots.caloriebot.access.AiRateLimiter;
import com.kuklin.manageapp.common.web.error.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.access.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.report.ReportService;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.access.telegram.FeatureLimitNotifier;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.weight.telegram.CalorieWeightHistoryUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;


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
    private final CalorieMenuButtons menuButtons;
    private final ReportPeriodKeyboards periodKeyboards;
    private final UserSettingsService userSettingsService;


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

            switch (parts[1]) {
                case ReportPeriodKeyboards.ACTION_PERIOD -> {
                    showPeriodMenu(chatId, messageId, telegramUser.getAppUserId());
                    return;
                }
                case ReportPeriodKeyboards.ACTION_CALENDAR -> {
                    showCalendar(chatId, messageId, telegramUser.getAppUserId(), parts);
                    return;
                }
                case ReportPeriodKeyboards.ACTION_RANGE -> {
                    processRange(chatId, telegramUser.getAppUserId(), parts);
                    return;
                }
                default -> { }
            }

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

        } catch (AiUnavailableException | ErrorResponseException e) {
            // Свой текст («ИИ не отвечает», текст ошибки сервиса) отправит фасад — не подменяем «ошибкой данных»
            throw e;
        } catch (Exception e) {
            log.error("{} Callback error! Data: {}", calorieTelegramBot.getBotUsername(), data, e);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    calorieTexts.get(telegramUser.getAppUserId(), BotTextKeys.REPORT_DATA_ERROR));
        }
    }

    private void showPeriodMenu(Long chatId, Integer messageId, Long appUserId) {
        AppLanguage language = calorieTexts.languageOf(appUserId);
        calorieTelegramBot.sendEditMessage(chatId,
                calorieTexts.get(language, BotTextKeys.REPORT_PERIOD_CHOOSE, ReportPeriodKeyboards.MAX_PERIOD_DAYS),
                messageId, periodKeyboards.buildPeriodMenu(language, getToday(appUserId)));
    }

    /** «CAL 202611» — выбор первого дня, «CAL 202611 20261109» — первый выбран, выбор последнего. */
    private void showCalendar(Long chatId, Integer messageId, Long appUserId, String[] parts) {
        if (parts.length < 3) return;
        AppLanguage language = calorieTexts.languageOf(appUserId);
        YearMonth month = ReportPeriodKeyboards.parseMonth(parts[2]);
        LocalDate start = parts.length > 3 ? ReportPeriodKeyboards.parseDate(parts[3]) : null;

        String text = start == null
                ? calorieTexts.get(language, BotTextKeys.REPORT_PERIOD_PICK_START)
                : calorieTexts.get(language, BotTextKeys.REPORT_PERIOD_PICK_END,
                ReportPeriodKeyboards.formatPeriod(start, start, language.getLocale()), ReportPeriodKeyboards.MAX_PERIOD_DAYS);
        calorieTelegramBot.sendEditMessage(chatId, text, messageId,
                periodKeyboards.buildCalendar(language, getToday(appUserId), month, start));
    }

    /** «RANGE 20261109 20261115» — собрать PDF за эти дни. */
    private void processRange(Long chatId, Long appUserId, String[] parts) {
        if (parts.length < 4) return;
        LocalDate from = ReportPeriodKeyboards.parseDate(parts[2]);
        LocalDate to = ReportPeriodKeyboards.parseDate(parts[3]);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        // Кнопки собирает бот, но старое сообщение могли нажать позже: «прошлая неделя» уже не та, сегодня в другом поясе
        if (days < 1 || days > ReportPeriodKeyboards.MAX_PERIOD_DAYS || to.isAfter(getToday(appUserId))) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    calorieTexts.get(appUserId, BotTextKeys.REPORT_PERIOD_INVALID, ReportPeriodKeyboards.MAX_PERIOD_DAYS),
                    TelegramKeyboard.builder().row(TelegramKeyboard.button(
                            calorieTexts.get(appUserId, BotTextKeys.REPORT_BUTTON_PERIOD),
                            ReportPeriodKeyboards.buildPeriodMenuData())).build(), null);
            return;
        }

        AiRateLimiter.Decision decision = aiRateLimiter.tryAcquire(appUserId);
        if (!decision.allowed()) {
            featureLimitNotifier.sendAiRateLimited(chatId, appUserId, decision);
            return;
        }

        calorieTelegramBot.sendChatActionTyping(chatId);
        executeWithAwait(chatId, appUserId, () -> handlePeriodReport(chatId, appUserId, from, to, days));
    }

    private void handlePeriodReport(Long chatId, Long appUserId, LocalDate from, LocalDate to, long days) {
        try {
            byte[] report = (days <= ReportKind.WEEK.getDays()
                    ? reportService.buildShortPeriodPdfReportOrNull(appUserId, from, to)
                    : reportService.buildLongPeriodPdfReportOrNull(appUserId, from, to)
            ).getOrThrow();
            if (report == null || report.length == 0) {
                calorieTelegramBot.sendReturnedMessage(chatId, calorieTexts.get(appUserId, PdfTextKeys.FAILED));
                return;
            }
            AppLanguage language = calorieTexts.languageOf(appUserId);
            String period = ReportPeriodKeyboards.formatPeriod(from, to, language.getLocale());
            calorieTelegramBot.sendDocument(chatId, report, "report-" + from + "_" + to + ".pdf",
                    calorieTexts.get(language, PdfTextKeys.CAPTION_PERIOD, period.toUpperCase(language.getLocale())));
        } catch (MissingFeatureException e) {
            featureLimitNotifier.sendLimitReached(chatId, appUserId, e.getRequiredFeature());
        }
    }

    private LocalDate getToday(Long appUserId) {
        return LocalDate.now(userSettingsService.getZoneId(appUserId));
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
            // «Свой период» — рядом с недельным и месячным, перед отчётом по весу
            if (type == ReportType.WEIGHT) {
                builder.row(TelegramKeyboard.button(calorieTexts.get(appUserId, BotTextKeys.REPORT_BUTTON_PERIOD),
                        ReportPeriodKeyboards.buildPeriodMenuData()));
            }
            builder.row(TelegramKeyboard.button(calorieTexts.get(appUserId, type.getButtonKey()), base + type.name()));
        }

        builder.row(menuButtons.buildBackToMenuButton(appUserId));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_REPORT.getCommandText();
    }
}