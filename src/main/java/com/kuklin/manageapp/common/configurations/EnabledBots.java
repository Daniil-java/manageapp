package com.kuklin.manageapp.common.configurations;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Какие боты включены — одно место для всего приложения.
 * Выключенный бот не регистрируется в Telegram (TelegramBotsStarter)
 * и его шедулеры не запускаются (классы с {@link BotScheduler}).
 * <p>
 * Список выключенных — bots.disabled (переменная BOTS_DISABLED), через запятую:
 * BOTS_DISABLED=HH_BOT,KWORK,AVIA_BOT. Пусто — включены все. Неизвестное имя — приложение не стартует.
 */
@Component
@Slf4j
public class EnabledBots {

    private final Set<BotIdentifier> disabled;

    public EnabledBots(@Value("${bots.disabled:}") Set<BotIdentifier> disabled) {
        this.disabled = disabled.isEmpty() ? EnumSet.noneOf(BotIdentifier.class) : EnumSet.copyOf(disabled);
        if (!this.disabled.isEmpty()) {
            log.info("Disabled bots (bots + schedulers): {}", this.disabled);
        }
    }

    public boolean isEnabled(BotIdentifier botIdentifier) {
        return !disabled.contains(botIdentifier);
    }
}
