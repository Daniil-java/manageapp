package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Оповещение всех пользователей.
 * <p>
 * Рассылка идёт в своём потоке: обработчик сразу отвечает админу и освобождает inProcess и поток @Async.
 * Шлём только тем, кто не блокировал бота, не быстрее {@link #MESSAGES_PER_SECOND} в секунду
 * (лимит Telegram — около 30 сообщений в секунду на бота, сверх него — 429). Клавиатура — одна на язык.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminMessageSenderUpdateHandler implements CalorieBotUpdateHandler {
    private static final long ADMIN_TELEGRAM_ID = 425120436L;
    // С запасом от лимита Telegram: бот в это время отвечает и остальным пользователям
    static final int MESSAGES_PER_SECOND = 20;
    private static final int MAX_ATTEMPTS = 3;

    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramUserService telegramUserService;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;
    // Один поток — две рассылки подряд не делят лимит Telegram, а встают в очередь
    private final ExecutorService broadcastExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "calorie-broadcast");
        thread.setDaemon(true);
        return thread;
    });

    enum SendResult { SENT, BLOCKED, FAILED }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!telegramUser.getTelegramId().equals(ADMIN_TELEGRAM_ID)) return;
        String text = update.getMessage().getText().substring(getHandlerListName().length()).strip();
        if (text.isEmpty()) {
            calorieTelegramBot.sendReturnedMessage(ADMIN_TELEGRAM_ID, "Usage: " + getHandlerListName() + " <text>");
            return;
        }

        List<TelegramUser> recipients = telegramUserService.getActiveUsersByBot(BotIdentifier.CALORIE_BOT);
        calorieTelegramBot.sendReturnedMessage(ADMIN_TELEGRAM_ID, "Broadcast queued: " + recipients.size() + " recipients");
        broadcastExecutor.submit(() -> broadcast(recipients, text));
    }

    void broadcast(List<TelegramUser> recipients, String text) {
        Map<AppLanguage, ReplyKeyboardMarkup> keyboards = new EnumMap<>(AppLanguage.class);
        int sent = 0, blocked = 0, failed = 0;
        long pauseMillis = 1000L / MESSAGES_PER_SECOND;
        try {
            for (TelegramUser recipient : recipients) {
                ReplyKeyboardMarkup keyboard = keyboards.computeIfAbsent(
                        texts.languageOf(recipient.getAppUserId()), menuButtons::buildKeyboard);
                switch (send(recipient.getTelegramId(), text, keyboard)) {
                    case SENT -> sent++;
                    case BLOCKED -> blocked++;
                    case FAILED -> failed++;
                }
                Thread.sleep(pauseMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Broadcast interrupted after {} of {} recipients", sent + blocked + failed, recipients.size());
        } catch (Exception e) {
            log.error("Broadcast failed after {} of {} recipients", sent + blocked + failed, recipients.size(), e);
        }
        log.info("Broadcast done: sent {}, blocked {}, failed {}", sent, blocked, failed);
        calorieTelegramBot.sendReturnedMessage(ADMIN_TELEGRAM_ID,
                "Broadcast done: sent " + sent + ", blocked " + blocked + ", failed " + failed);
    }

    /**
     * Отправка одному получателю. 429 — ждём сколько сказал Telegram и пробуем снова;
     * 403 — бот заблокирован, помечаем пользователя (больше в рассылки не попадёт);
     * 400 при HTML — текст не разобрался, шлём простым текстом.
     */
    SendResult send(long chatId, String text, ReplyKeyboardMarkup keyboard) throws InterruptedException {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .parseMode(ParseMode.HTML)
                .build();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                calorieTelegramBot.execute(message);
                return SendResult.SENT;
            } catch (TelegramApiRequestException e) {
                Integer code = e.getErrorCode();
                if (code != null && code == 403) {
                    telegramUserService.deactivateUser(chatId, BotIdentifier.CALORIE_BOT);
                    return SendResult.BLOCKED;
                }
                if (code != null && code == 429 && e.getParameters() != null && e.getParameters().getRetryAfter() != null) {
                    log.warn("Broadcast hit rate limit, waiting {} s", e.getParameters().getRetryAfter());
                    Thread.sleep(e.getParameters().getRetryAfter() * 1000L);
                    continue;
                }
                if (code != null && code == 400 && message.getParseMode() != null) {
                    message.setParseMode(null);
                    continue;
                }
                log.error("Broadcast to {} failed: {}", chatId, e.getMessage());
                return SendResult.FAILED;
            } catch (TelegramApiException e) {
                log.error("Broadcast to {} failed: {}", chatId, e.getMessage());
                return SendResult.FAILED;
            }
        }
        return SendResult.FAILED;
    }

    @PreDestroy
    void shutdown() {
        broadcastExecutor.shutdownNow();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_ADMIN_MESSAGE.getCommandText();
    }
}
