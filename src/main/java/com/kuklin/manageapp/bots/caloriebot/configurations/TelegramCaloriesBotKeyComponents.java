package com.kuklin.manageapp.bots.caloriebot.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramCaloriesBotKeyComponents {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramCaloriesBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("CALORY_BOT_TOKEN");
        log.info("Generation key initiated (CALORY_BOT_TOKEN)");
        this.aiKey = environment.getProperty("GENERATION_TOKEN");
        log.info("Ai key initiated (CALORY_BOT)");
    }
}
