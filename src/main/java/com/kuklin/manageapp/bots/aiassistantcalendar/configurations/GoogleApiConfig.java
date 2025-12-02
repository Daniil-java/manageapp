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
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Optional;

@Configuration
@Slf4j
public class GoogleApiConfig {

    @Bean
    public Calendar googleCalendarService() {
        try {
            Optional<Credentials> credsOpt = loadServiceAccountCredentialsOptional();

            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            // Если креды есть — используем их; если нет — "пустой" инициалайзер (NO-AUTH)
            HttpRequestInitializer requestInitializer = credsOpt
                    .<HttpRequestInitializer>map(HttpCredentialsAdapter::new)
                    .orElse(request -> { /* no-op */ });

            if (credsOpt.isEmpty()) {
                log.warn("Service account JSON не найден. Создаю NO-AUTH клиент Google Calendar. " +
                        "Приложение поднимется, но запросы к API будут получать 401.");
            }

            return new Calendar.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName(credsOpt.isPresent()
                            ? "Spring Google Calendar Adder"
                            : "Spring Google Calendar Adder (NO-AUTH STUB)")
                    .build();

        } catch (Exception e) {
            // На крайняк — жёсткий фолбэк: вообще без авторизации, чтобы не уронить контекст
            log.error("Не удалось инициализировать Google Calendar клиент, создаю STUB", e);
            NetHttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            return new Calendar.Builder(transport, jsonFactory, request -> { /* no-op */ })
                    .setApplicationName("Spring Google Calendar Adder (HARD STUB)")
                    .build();
        }
    }

    /**
     * Пытается загрузить креды; если их нет/ошибка — возвращает Optional.empty()
     */
    private Optional<Credentials> loadServiceAccountCredentialsOptional() {
        try {
            String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            InputStream in;

            if (path != null && !path.isBlank()) {
                in = new FileInputStream(path);
            } else {
                in = getClass().getResourceAsStream("/service-account.json");
                if (in == null) return Optional.empty();
            }

            try (in) {
                return Optional.of(
                        ServiceAccountCredentials.fromStream(in).createScoped(CalendarScopes.CALENDAR)
                );
            }
        } catch (Exception ex) {
            log.warn("Не удалось загрузить service-account креды: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
