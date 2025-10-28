package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;

/**
 * Фабрика Calendar-клиента "от лица пользователя" (только если он авторизовался).
 * Берём access_token из InMemoryUserTokens и строим GoogleCredentials.
 */
@Component
@RequiredArgsConstructor
public class UserCalendarClientFactory {

    private final InMemoryUserTokens tokens;

    public Calendar forChat(Long chatId) {
        var e = tokens.get(chatId);
        if (e == null || e.getAccessToken() == null) return null;

        try {
            var http = GoogleNetHttpTransport.newTrustedTransport();
            var json = JacksonFactory.getDefaultInstance();

            var at = new AccessToken(e.getAccessToken(), e.getAccessExpiresAt() != null ? Date.from(e.getAccessExpiresAt()) : null);
            GoogleCredentials creds = GoogleCredentials.create(at).createScoped(Collections.singleton(CalendarScopes.CALENDAR));

            return new Calendar.Builder(http, json, new HttpCredentialsAdapter(creds))
                    .setApplicationName("ManageApp (User OAuth)")
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }
}
