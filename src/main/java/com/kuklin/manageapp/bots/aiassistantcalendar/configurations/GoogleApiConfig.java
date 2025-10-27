package com.kuklin.manageapp.bots.aiassistantcalendar.configurations;


import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

@Configuration
@Slf4j
public class GoogleApiConfig {

    private static final String ENV = "CAL_ASSISTANT_GOOGLE_CREDENTIALS_B64";

    @Bean
    public Calendar assistantCalendarService() {
        try {
            Optional<Credentials> credsOpt = loadAssistantServiceAccount();
            NetHttpTransport http = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory json = JacksonFactory.getDefaultInstance();

            HttpRequestInitializer init = credsOpt
                    .<HttpRequestInitializer>map(HttpCredentialsAdapter::new)
                    .orElse(req -> { /* NO-AUTH */ });

            if (credsOpt.isEmpty()) {
                log.warn("Assistant Calendar: creds not found in {} → NO-AUTH client (вызовы вернут 401).", ENV);
            }

            return new Calendar.Builder(http, json, init)
                    .setApplicationName("ManageApp Assistant Calendar")
                    .build();

        } catch (Exception e) {
            log.error("Assistant Calendar: init failed, fallback NO-AUTH", e);
            return new Calendar.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), req -> {})
                    .setApplicationName("ManageApp Assistant Calendar (NO-AUTH)")
                    .build();
        }
    }

    private Optional<Credentials> loadAssistantServiceAccount() {
        try (InputStream in = resolveAssistantCredsStream()) {
            if (in == null) return Optional.empty();

            // Читаем как сервис-аккаунт
            ServiceAccountCredentials sa = ServiceAccountCredentials.fromStream(in);

            // Добавляем нужный скоуп → это уже GoogleCredentials
            GoogleCredentials scoped = sa.createScoped(Collections.singleton(CalendarScopes.CALENDAR));

            // Логнем email сервис-аккаунта (берём из исходного sa)
            log.info("Assistant Calendar SA loaded: email={}", sa.getClientEmail());

            return Optional.of(scoped); // возвращаем как Credentials
        } catch (Exception e) {
            log.warn("Assistant Calendar: cannot load credentials from {}: {}", ENV, e.getMessage());
            return Optional.empty();
        }
    }

    private InputStream resolveAssistantCredsStream() {
        String b64 = System.getenv(ENV);
        if (b64 == null || b64.isBlank()) return null;
        log.info("Assistant Calendar: loading creds from env {}", ENV);
        byte[] bytes = Base64.getDecoder().decode(b64); // декодируем байты, не ломаем \n в private_key
        return new ByteArrayInputStream(bytes);
    }
}
