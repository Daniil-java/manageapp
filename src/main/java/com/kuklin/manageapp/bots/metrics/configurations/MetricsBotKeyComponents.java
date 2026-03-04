package com.kuklin.manageapp.bots.metrics.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class MetricsBotKeyComponents {
    private final String key;

    @Autowired
    public MetricsBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("METRICS_BOT_TOKEN");
        log.info("Generation key initiated (METRICS_BOT_TOKEN)");

    }
}
