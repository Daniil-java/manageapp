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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class GoogleApiConfig {
    @Bean
    public Calendar googleCalendarService() throws Exception {
        // Загружаем учетные данные сервисного аккаунта
        Credentials creds = loadServiceAccountCredentials();

        // Оборачиваем их в HttpRequestInitializer, чтобы Google API мог авторизовывать запросы
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(creds);

        // Создаём защищённый HTTP-транспорт для общения с Google API
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        // JsonFactory отвечает за сериализацию/десериализацию JSON в Google API
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        return new Calendar.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("Spring Google Calendar Adder")
                .build();
    }

    /**
     * Загружает учетные данные сервисного аккаунта.
     * 1. Сначала пытается взять путь из переменной окружения GOOGLE_APPLICATION_CREDENTIALS.
     * 2. Если переменная не задана — ищет файл service-account.json в resources.
     * 3. Если не найдено — кидает исключение.
     */
    private Credentials loadServiceAccountCredentials() throws IOException {
        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        InputStream in;

        if (path != null && !path.isBlank()) {
            // Если путь задан в переменной окружения — читаем файл оттуда
            in = new FileInputStream(path);
        } else {
            // Иначе пробуем найти файл в ресурсах проекта
            in = getClass().getResourceAsStream("/service-account.json");
            if (in == null) {
                throw new IllegalStateException(
                        "Service account JSON not found. " +
                                "Set GOOGLE_APPLICATION_CREDENTIALS or place service-account.json in resources."
                );
            }
        }

        try (in) {
            // Создаём объект Credentials и ограничиваем его область действия только Google Calendar API
            return ServiceAccountCredentials.fromStream(in)
                    .createScoped(CalendarScopes.CALENDAR);
        }
    }

}
