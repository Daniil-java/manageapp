package com.kuklin.manageapp.bots.bookingbot.configurations;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
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
public class GoogleSheetsConfig {

    @Bean
    public Sheets sheetsService() {
        try {
            Optional<Credentials> credsOpt = loadServiceAccountCredentialsOptional();

            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            HttpRequestInitializer requestInitializer = credsOpt
                    .<HttpRequestInitializer>map(HttpCredentialsAdapter::new)
                    .orElse(request -> { /* NO-AUTH */ });

            if (credsOpt.isEmpty()) {
                log.warn("credentials.json не найден. Создаю NO-AUTH клиент Google Sheets. " +
                        "Приложение поднимется, но запросы к API вернут 401/403.");
            }

            return new Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName(credsOpt.isPresent()
                            ? "ManageApp Sheets Client"
                            : "ManageApp Sheets Client (NO-AUTH STUB)")
                    .build();

        } catch (Exception e) {
            // Жёсткий фолбэк — вообще без авторизации, чтобы контекст не упал
            log.error("Не удалось инициализировать Google Sheets клиент, создаю HARD STUB", e);
            NetHttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            return new Sheets.Builder(transport, jsonFactory, request -> { /* NO-AUTH */ })
                    .setApplicationName("ManageApp Sheets Client (HARD STUB)")
                    .build();
        }
    }

    /**
     * Пытается загрузить креды сервисного аккаунта из
     * 1) переменной окружения GOOGLE_APPLICATION_CREDENTIALS (путь к JSON)
     * 2) classpath:/credentials.json
     * Если не найдено/ошибка — Optional.empty()
     */
    private Optional<Credentials> loadServiceAccountCredentialsOptional() {
        try {
            String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            InputStream in;

            if (path != null && !path.isBlank()) {
                in = new FileInputStream(path);
            } else {
                in = getClass().getResourceAsStream("/credentials.json");
                if (in == null) return Optional.empty();
            }

            try (in) {
                return Optional.of(
                        ServiceAccountCredentials.fromStream(in)
                                .createScoped(SheetsScopes.SPREADSHEETS)
                );
            }
        } catch (Exception ex) {
            log.warn("Не удалось загрузить credentials.json: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
