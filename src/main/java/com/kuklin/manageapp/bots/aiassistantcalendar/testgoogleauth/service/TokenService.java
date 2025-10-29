package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.AssistantGoogleOAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final AssistantGoogleOAuthRepository repo;
    private final GoogleOAuthHttpClient google;
    private final CryptoService crypto;

    @Transactional
    public void saveFromAuthCallback(long chatId, GoogleOAuthHttpClient.TokenResponse t, GoogleOAuthHttpClient.UserInfo u, Instant now) {
        var e = repo.findById(chatId).orElseGet(() ->
                AssistantGoogleOAuth.builder().telegramId(chatId).build());

        e.setGoogleSub(u.sub());
        e.setEmail(u.email());
        e.setScope(t.scope());
        e.setAccessToken(t.access_token());
        e.setAccessExpiresAt(now.plusSeconds(Optional.ofNullable(t.expires_in()).orElse(3600L)));
        if (t.refresh_token() != null && !t.refresh_token().isBlank()) {
            e.setRefreshTokenEnc(crypto.encrypt(t.refresh_token()));
        }
        e.setLastRefreshAt(now);
        repo.save(e);
    }

    @Transactional
    public String ensureAccessToken(long chatId) {
        var e = repo.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Not authorized"));
        if (e.getAccessExpiresAt() != null
                && e.getAccessExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return e.getAccessToken();
        }
        var rt = crypto.decrypt(e.getRefreshTokenEnc());
        var r = google.refresh(rt);
        e.setAccessToken(r.access_token());
        e.setAccessExpiresAt(Instant.now().plusSeconds(r.expires_in() == null ? 3600 : r.expires_in()));
        e.setLastRefreshAt(Instant.now());
        repo.save(e);
        return e.getAccessToken();
    }

    @Transactional
    public void revokeAndDelete(long chatId) {
        var e = repo.findById(chatId).orElse(null);
        if (e != null && e.getRefreshTokenEnc() != null) {
            google.revoke(crypto.decrypt(e.getRefreshTokenEnc()));
        }
        if (e != null) repo.delete(e);
    }

    @Transactional(readOnly = true)
    public AssistantGoogleOAuth get(long chatId) {
        return repo.findById(chatId).orElseThrow();
    }

    @Transactional
    public void setDefaultCalendar(long chatId, String calendarId) {
        var e = repo.findById(chatId).orElseThrow();
        e.setDefaultCalendarId(calendarId);
        repo.save(e);
    }
}
