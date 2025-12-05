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
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
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
                log.warn("Google creds not found. Sheets client will be NO-AUTH (401/403 on calls).");
            }

            return new Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName(credsOpt.isPresent() ? "ManageApp Sheets Client"
                            : "ManageApp Sheets Client (NO-AUTH STUB)")
                    .build();

        } catch (Exception e) {
            log.error("Failed to init Google Sheets client, creating HARD STUB", e);
            return new Sheets.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(),
                    request -> { /* NO-AUTH */ })
                    .setApplicationName("ManageApp Sheets Client (HARD STUB)")
                    .build();
        }
    }

    private Optional<Credentials> loadServiceAccountCredentialsOptional() {
        try (InputStream in = resolveCredentialsInputStream()) {
            if (in == null) return Optional.empty();

            // Нужные скоупы: листы + (опционально) доступ к файлу в Drive, если пишешь/создаешь
            List<String> scopes = List.of(SheetsScopes.SPREADSHEETS /*, SheetsScopes.DRIVE */);

            ServiceAccountCredentials sac = (ServiceAccountCredentials)
                    ServiceAccountCredentials.fromStream(in).createScoped(SheetsScopes.SPREADSHEETS);

            sac.refreshIfExpired(); // получить токен
            var token = sac.getAccessToken();
            log.info("Google SA loaded: email={} token_exp={}", sac.getClientEmail(),
                    token != null ? token.getExpirationTime() : "null");
            return Optional.of(sac);
        } catch (Exception ex) {
            log.error("Cannot load Google credentials: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private InputStream resolveCredentialsInputStream() throws IOException, GeneralSecurityException {
        String b64 = System.getenv("GOOGLE_CREDENTIALS_B64");
        if (b64 != null && !b64.isBlank()) {
            log.info("Load Google creds from GOOGLE_CREDENTIALS_B64");
            byte[] decoded = Base64.getDecoder().decode(b64);
            return new ByteArrayInputStream(decoded);
        }
        log.error(BotIdentifier.BOOKING_BOT.getBotUsername() + ": Google credentials error!  ");
        return null;
    }
}
