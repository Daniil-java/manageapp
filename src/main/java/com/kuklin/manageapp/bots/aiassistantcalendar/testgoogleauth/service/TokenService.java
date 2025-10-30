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
    private static final Long DEFAULT_EXPIRES_TIME = 3600L;

    @Transactional
    public void saveFromAuthCallback(Long telegramId, GoogleOAuthHttpClient.TokenResponse tokenResponse, GoogleOAuthHttpClient.UserInfo userInfo, Instant now) {
        AssistantGoogleOAuth auth = repo.findById(telegramId).orElseGet(() ->
                AssistantGoogleOAuth.builder().telegramId(telegramId).build());

        auth
                .setGoogleSub(userInfo.sub())
                .setEmail(userInfo.email())
                .setScope(tokenResponse.scope())
                .setScope(tokenResponse.scope())
                .setAccessToken(tokenResponse.access_token())
                .setAccessExpiresAt(now.plusSeconds(Optional.ofNullable(tokenResponse.expires_in()).orElse(DEFAULT_EXPIRES_TIME)))
                .setLastRefreshAt(now)
        ;

        if (tokenResponse.refresh_token() != null && !tokenResponse.refresh_token().isBlank()) {
            auth.setRefreshTokenEnc(crypto.encrypt(tokenResponse.refresh_token()));
        }
        repo.save(auth);
    }

    @Transactional
    public String ensureAccessToken(long telegramId) {
        AssistantGoogleOAuth auth = repo.findById(telegramId)
                .orElseThrow(() -> new IllegalStateException("Not authorized"));

        if (auth.getAccessExpiresAt() != null
                && auth.getAccessExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return auth.getAccessToken();
        }
        String rt = crypto.decrypt(auth.getRefreshTokenEnc());
        GoogleOAuthHttpClient.TokenResponse r = google.refresh(rt);

        auth
                .setAccessToken(r.access_token())
                .setAccessExpiresAt(Instant.now().plusSeconds(r.expires_in() == null ? DEFAULT_EXPIRES_TIME : r.expires_in()))
                .setLastRefreshAt(Instant.now());

        repo.save(auth);
        return auth.getAccessToken();
    }

    @Transactional
    public void revokeAndDelete(long telegramId) {
        AssistantGoogleOAuth auth = repo.findById(telegramId).orElse(null);
        if (auth != null && auth.getRefreshTokenEnc() != null) {
            google.revoke(crypto.decrypt(auth.getRefreshTokenEnc()));
        }
        if (auth != null) repo.delete(auth);
    }

    @Transactional(readOnly = true)
    public AssistantGoogleOAuth get(long telegramId) {
        return repo.findById(telegramId).orElseThrow();
    }

    @Transactional
    public void setDefaultCalendar(long telegramId, String calendarId) {
        AssistantGoogleOAuth auth = repo.findById(telegramId).orElseThrow();
        repo.save(auth.setDefaultCalendarId(calendarId));
    }
}
