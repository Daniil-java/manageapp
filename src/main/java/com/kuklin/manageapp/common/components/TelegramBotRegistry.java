package com.kuklin.manageapp.common.components;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Реестр всех Telegram-ботов приложения.
 * <p>
 * Spring автоматически находит все бины, реализующие {@link TelegramBot},
 * и передаёт их в конструктор как список. На основании этого списка
 * реестр строит карту {@code Map<BotIdentifier, TelegramBot>}.
 * <p>
 * Используется как единая точка входа, чтобы по {@link BotIdentifier}
 * получить нужного бота и отправить через него сообщения.
 */
@Component
@Slf4j
public class TelegramBotRegistry {

    /**
     * Отображение BotIdentifier → конкретный TelegramBot.
     * <p>
     * Заполняется один раз в конструкторе и далее не меняется.
     */
    private final Map<BotIdentifier, TelegramBot> botsById;

    public TelegramBotRegistry(List<TelegramBot> bots) {
        this.botsById = bots.stream()
                .collect(Collectors.toMap(
                        TelegramBot::getBotIdentifier,
                        Function.identity()
                ));
    }

    public TelegramBot get(BotIdentifier botIdentifier) {
        TelegramBot bot = botsById.get(botIdentifier);
        if (bot == null) {
            log.error("Bot not found!");
            throw new IllegalArgumentException("Bot not found for id: " + botIdentifier);
        }
        return bot;
    }
}
