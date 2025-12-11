package com.kuklin.manageapp.bots.pomidorotimer.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramPomidoroTimerBotKeyComponents {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramPomidoroTimerBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("TGBOT_TOKEN");
        log.info("Generation key initiated (TGBOT_TOKEN)");
        this.aiKey = environment.getProperty("POMIDORO_GENERATION_TOKEN");
        log.info("Ai key initiated (POMIDORO_GENERATION_TOKEN)");
    }
}
