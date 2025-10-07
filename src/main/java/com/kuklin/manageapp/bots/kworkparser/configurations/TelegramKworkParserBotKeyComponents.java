package com.kuklin.manageapp.bots.kworkparser.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class TelegramKworkParserBotKeyComponents {
    private final String key;
    private final String aiKey;

    @Autowired
    public TelegramKworkParserBotKeyComponents(Environment environment) {
//        this.key = environment.getProperty("KWORK_PARSER_BOT_TOKEN");
        this.key = "8211611863:AAGoEGrqekzU-QVgIDZjElOPUeQUt691oNs";
        log.info("Generation key initiated (KWORK_PARSER_BOT_TOKEN)");
        this.aiKey = environment.getProperty("GENERATION_TOKEN");
        log.info("Ai key initiated (KWORK_PARSER_TOKEN)");
    }
}
