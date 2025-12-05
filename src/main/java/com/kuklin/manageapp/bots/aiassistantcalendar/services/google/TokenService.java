package com.kuklin.manageapp.bots.aiassistantcalendar.services.google;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.AssistantGoogleOAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {
    private final AssistantGoogleOAuthRepository repo;
    private final GoogleOAuthHttpClient google;
    private final CryptoService crypto;
    private static final Long DEFAULT_EXPIRES_TIME = 3600L;

    @Transactional
    public AssistantGoogleOAuth saveFromAuthCallback(Long telegramId, GoogleOAuthHttpClient.TokenResponse tokenResponse, GoogleOAuthHttpClient.UserInfo userInfo, Instant now) {
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
        return repo.save(auth);
    }

    @Transactional
    public String ensureAccessTokenOrNull(long telegramId) throws TokenRefreshException {
        AssistantGoogleOAuth auth = repo.findById(telegramId)
                .orElse(null);
        if (auth == null) return null;

        if (auth.getAccessExpiresAt() != null
                && auth.getAccessExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return auth.getAccessToken();
        }

        String rt = crypto.decrypt(auth.getRefreshTokenEnc());

        GoogleOAuthHttpClient.TokenResponse r = null;
        try {
            r = google.refresh(rt);
        } catch (TokenRefreshException e) {
            if (e.getReason().equals(TokenRefreshException.Reason.INVALID_GRANT)) {
                //Удаляем запись, т.к. мы больше не можем обновить токен.
                revokeAndDelete(telegramId);
            }
            throw e;
        }

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
            try {
                google.revoke(crypto.decrypt(auth.getRefreshTokenEnc()));
            } catch (Exception e) {
                log.error("Failed to revoke token for telegramId={}", telegramId, e);
            }
        }
        if (auth != null) repo.delete(auth);
    }

    @Transactional(readOnly = true)
    public AssistantGoogleOAuth findByTelegramIdOrNull(long telegramId) {
        return repo.findById(telegramId).orElse(null);
    }

    @Transactional
    public AssistantGoogleOAuth setDefaultCalendarOrNull(long telegramId, String calendarId) {
        AssistantGoogleOAuth auth = repo.findById(telegramId).orElse(null);
        if (auth == null) return null;
        return repo.save(auth.setDefaultCalendarId(calendarId));
    }

    @Transactional
    public List<AssistantGoogleOAuth> getAll() {
        return repo.findAll();
    }

    public AssistantGoogleOAuth getByTelegramIdOrNull(Long telegramId) {
        return repo.findByTelegramId(telegramId).orElse(null);
    }
}
