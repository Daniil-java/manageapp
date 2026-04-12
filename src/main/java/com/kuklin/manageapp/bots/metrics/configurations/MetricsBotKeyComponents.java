package com.kuklin.manageapp.bots.metrics.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Getter
@Component
public class MetricsBotKeyComponents {
    private final String key;
    private final List<Long> adminIds;

    @Autowired
    public MetricsBotKeyComponents(Environment environment) {
        this.key = environment.getProperty("METRICS_BOT_TOKEN");
        log.info("Generation key initiated (METRICS_BOT_TOKEN)");
        this.adminIds = getAdminIdsList(environment.getProperty("METRICS_ADMIN_IDS"));
        log.info("Admins ids initiated!");
    }

    private List<Long> getAdminIdsList(String admIds) {
        if (admIds == null || admIds.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(admIds.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(id -> {
                    try {
                        return Stream.of(Long.parseLong(id));
                    } catch (NumberFormatException e) {
                        log.error("{}: invalid admin id '{}'", getClass().getSimpleName(), id);
                        return Stream.empty();
                    }
                })
                .toList();
    }

}

