package com.kuklin.manageapp.bots.aiassistantcalendar.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramAiAssistantCalendarBotKeyComponents {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramAiAssistantCalendarBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("CALEN_BOT_TOKEN");
        log.info("Generation key initiated (CALEN_BOT_TOKEN)");
        this.aiKey = environment.getProperty("CALEN_GENERATION_TOKEN");
        log.info("Ai key initiated (CALEN_GENERATION_TOKEN)");
    }
}
