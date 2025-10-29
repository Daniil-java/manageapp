package com.kuklin.manageapp.bots.deparrbot.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramAviaBotKeyComponent {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramAviaBotKeyComponent(Environment environment) {
        this.key = environment.getProperty("DEPARR_BOT_TOKEN");
        log.info("Generation key initiated (DEPARR_BOT_TOKEN)");
        this.aiKey = environment.getProperty("GENERATION_TOKEN");
        log.info("Ai key initiated (DEPARR_PARSER_TOKEN)");
    }
}
