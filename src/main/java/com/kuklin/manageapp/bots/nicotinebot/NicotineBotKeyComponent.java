package com.kuklin.manageapp.bots.nicotinebot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class NicotineBotKeyComponent {
    private final String key;

    @Autowired
    public NicotineBotKeyComponent(Environment environment) {
        this.key = environment.getProperty("NICOTINE_BOT_TOKEN");
    }
}
