package com.kuklin.manageapp.bots.aiassistantcalendar.configurations;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
public class GoogleComponents {
    private final String clientSecret;
    private final String clientId;

    @Autowired
    public GoogleComponents(Environment environment) {
        this.clientSecret = environment.getProperty("GOOGLE_CALENDAR_KEY");
        this.clientId = environment.getProperty("GOOGLE_OAUTH_CLIENT_ID");
    }
}
