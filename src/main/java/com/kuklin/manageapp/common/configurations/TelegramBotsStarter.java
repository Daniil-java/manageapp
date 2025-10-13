package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Стартер Telegram-ботов.
 *
 * ЧТО ОН ДЕЛАЕТ:
 *  - Берёт из контекста готовый TelegramBotsApi и список всех твоих реализаций TelegramBot.
 *  - Один раз (даже с DevTools) регистрирует каждого бота в API.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@DependsOn("liquibase")
public class TelegramBotsStarter implements ApplicationListener<ApplicationReadyEvent> {
    private final TelegramBotsApi api;
    private final List<TelegramBot> bots;
    // Флажок, чтобы не запустить регистрацию дважды
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!started.compareAndSet(false, true)) return; // защита от двойного старта (DevTools и пр.)

        for (TelegramBot bot : bots) {
            try {
                api.registerBot(bot);
                log.info("Telegram bot registered: {}", bot.getBotUsername());
            } catch (TelegramApiException e) {
                throw new IllegalStateException("Failed to register bot: " + bot.getClass().getSimpleName(), e);
            }
        }
    }
}
