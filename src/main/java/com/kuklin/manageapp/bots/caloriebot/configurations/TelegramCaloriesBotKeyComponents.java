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
    private final String geminiAiKey;
    private final String deepseekAiKey;
    private final String claudeAiKey;

    @Autowired
    public TelegramCaloriesBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("CALORY_BOT_TOKEN");
        log.info("Generation key initiated (CALORY_BOT_TOKEN)");
        this.aiKey = environment.getProperty("CALORY_GENERATION_TOKEN");
        log.info("Ai key initiated (CALORY_GENERATION_TOKEN)");
        this.geminiAiKey = environment.getProperty("GEMINI_TOKEN");
        log.info("Gemini Ai key initiated (CALORY_BOT)");
        this.deepseekAiKey = environment.getProperty("DEEPSEEK_TOKEN");
        log.info("DeepSeek Ai key initiated (CALORY_BOT)");
        this.claudeAiKey = environment.getProperty("CLAUDE_TOKEN");
        log.info("Claude Ai key initiated (CALORY_BOT)");


    }
}
