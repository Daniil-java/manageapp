package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Компонент, отвечающий за ЖЦ регистрации Telegram-ботов.
 * Использует SmartLifecycle, чтобы стартовать в самом конце (после Liquibase)
 * и корректно останавливать ресурсы при завершении приложения.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DependsOn("liquibase") // если Liquibase точно есть
public class TelegramBotsStarter implements SmartLifecycle {

    private final TelegramBotsApi api;
    private final List<TelegramBot> bots;

    /**
     * Контейнер для ресурсов/сессий, которые нужно закрыть при остановке.
     * В разных версиях библиотеки registerBot(...) может возвращать сессию/ресурс.
     * Если возвращаемый объект реализует AutoCloseable — складываем сюда.
     */
    private final Set<AutoCloseable> sessions = ConcurrentHashMap.newKeySet();
    /** Флаг текущего состояния (запущено/остановлено) */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Автостарт вместе с контекстом */
    @Override public boolean isAutoStartup() { return true; }
    /** Фаза наибольшая — стартуем последними, останавливаемся первыми */
    @Override public int getPhase() { return Integer.MAX_VALUE; }
    /** Текущее состояние компонента */
    @Override public boolean isRunning() { return running.get(); }

    /**
     * Точка старта: регистрируем всех включённых ботов.
     * Ошибка регистрации конкретного бота не валит приложение.
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        int ok = 0;
        for (TelegramBot bot : bots) {
            // Фильтруем ботов, которых сейчас не хотим поднимать
            if (!isEnabled(bot)) {
                log.info("Skip bot: {}", safeName(bot));
                continue;
            }
            try {
                // зарегистрировали и сохранили сессию/ресурс для последующего закрытия
                var session = api.registerBot(bot);
                if (session instanceof AutoCloseable c) sessions.add(c);
                ok++;
                log.info("Registered bot: {} ({})", safeName(bot), bot.getBotIdentifier());
            } catch (Exception e) {
                log.error("Failed to register bot: {} ({})", safeName(bot), bot.getBotIdentifier(), e);
            }
        }
        if (ok == 0) {
            log.warn("No Telegram bots registered.");
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        for (var c : sessions) {
            try { c.close(); } catch (Exception e) { log.warn("Close bot session failed", e); }
        }
        sessions.clear();
    }

    private boolean isEnabled(TelegramBot bot) {
        var id = bot.getBotIdentifier();
        if (id.equals(BotIdentifier.ASSISTANT_BOT)) return false;
        return true;
    }

    private String safeName(TelegramBot bot) {
        try { return bot.getBotUsername(); }
        catch (Exception e) { return bot.getClass().getSimpleName(); }
    }
}