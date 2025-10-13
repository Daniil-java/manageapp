package com.kuklin.manageapp.bots.hhparserbot.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramHhParserBotKeyComponents {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramHhParserBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("HHTGBOT_TOKEN");
        log.info("Generation key initiated (workhunttg_bot)");
        this.aiKey = environment.getProperty("GENERATION_TOKEN");
        log.info("Ai key initiated (HHTGBOT_TOKEN)");
    }
}
