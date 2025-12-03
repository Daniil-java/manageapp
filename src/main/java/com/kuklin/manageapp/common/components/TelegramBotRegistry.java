package com.kuklin.manageapp.common.components;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TelegramBotRegistry {

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
