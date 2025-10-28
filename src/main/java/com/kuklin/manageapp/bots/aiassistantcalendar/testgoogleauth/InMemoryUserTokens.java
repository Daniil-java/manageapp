package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Существенно: это не БД.
 * Токены живут в памяти и исчезнут при рестарте. Для тестов — ок.
 */
@Service
public class InMemoryUserTokens {

    private final Map<Long, Entry> map = new ConcurrentHashMap<>();

    public void save(long chatId, GoogleOAuthController.TokenResponse t, Instant now) {
        Instant exp = now.plusSeconds(t.expires_in() != null ? t.expires_in() : 3600);
        map.put(chatId, new Entry(t.access_token(), t.refresh_token(), exp, t.scope()));
    }

    public Entry get(long chatId) { return map.get(chatId); }

    @Getter
    public static class Entry {
        private final String accessToken;
        private final String refreshToken; // можно не использовать в тестах
        private final Instant accessExpiresAt;
        private final String scope;

        public Entry(String accessToken, String refreshToken, Instant accessExpiresAt, String scope) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessExpiresAt = accessExpiresAt;
            this.scope = scope;
        }
    }
}
