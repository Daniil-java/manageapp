package com.kuklin.manageapp.bots.bookingbot.configurations;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
@Data
public class TelegramBookingBotKeyComponents {

    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramBookingBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("SLOT_MANAGER_BOT_TOKEN");
        log.info("Generation key initiated (SLOT_MANAGER_BOT_TOKEN)");
        this.aiKey = environment.getProperty("GENERATION_TOKEN");
        log.info("Ai key initiated (SLOT_MANAGER_BOT_TOKEN)");
    }
}
