package com.kuklin.manageapp.bots.channelposter.telegram;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Getter
@Component
public class ChannelPosterBotKeyComponent {
    private final String key;
    private final String aiKey;
    private final List<Long> adminIds;
    private final Long channelId;

    @Autowired
    public ChannelPosterBotKeyComponent(Environment environment) {
        this.key = environment.getProperty("CHANNELPOSTER_BOT_TOKEN");
        log.info("Generation key initiated (CHANNELPOSTER_BOT_TOKEN)");
        this.aiKey = environment.getProperty("CHANNELPOSTER_GENERATION_TOKEN");
        log.info("Ai key initiated (CHANNELPOSTER_GENERATION_TOKEN)");
        this.adminIds = getAdminIdsList(environment.getProperty("CHANNELPOSTER_ADMIN_IDS"));
        log.info("Admins ids initiated!");

        Long channelTempId;
        try {
            channelTempId = Long.parseLong(environment.getProperty("CHANNEL_ID"));
        } catch (Exception e) {
            channelTempId = 0L;
            log.error("Channel ID is wrong!");
        }
        this.channelId = channelTempId;
        log.info("Channel ID initiated!");
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
