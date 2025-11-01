package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.models.TokenRefreshException;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.AssistantGoogleOAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        log.info("ensureAccessTokenOrNull started");
        AssistantGoogleOAuth auth = repo.findById(telegramId)
                .orElse(null);

        log.info("Auth access: " + auth.getEmail());

        if (auth.getAccessExpiresAt() != null
                && auth.getAccessExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            log.info("token exist!");
            return auth.getAccessToken();
        }
        log.info("token expired");

        String rt = crypto.decrypt(auth.getRefreshTokenEnc());

        GoogleOAuthHttpClient.TokenResponse r = null;
        try {
            log.info("refresh token");
            r = google.refresh(rt);
            log.info("token updated");
        } catch (TokenRefreshException e) {
            if (e.getReason().equals(TokenRefreshException.Reason.INVALID_GRANT)) {
                //Удаляем запись, т.к. мы больше не можем обновить токен.
                log.info("delete auth");
                revokeAndDelete(telegramId);
            }
            throw e;
        }

        auth
                .setAccessToken(r.access_token())
                .setAccessExpiresAt(Instant.now().plusSeconds(r.expires_in() == null ? DEFAULT_EXPIRES_TIME : r.expires_in()))
                .setLastRefreshAt(Instant.now());

        log.info("auth save");
        repo.save(auth);
        log.info("auth saved");
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
    public AssistantGoogleOAuth findByTelegramIdOrNull(long telegramId) {
        return repo.findById(telegramId).orElse(null);
    }

    @Transactional
    public AssistantGoogleOAuth setDefaultCalendarOrNull(long telegramId, String calendarId) {
        AssistantGoogleOAuth auth = repo.findById(telegramId).orElse(null);
        if (auth == null) return null;
        return repo.save(auth.setDefaultCalendarId(calendarId));
    }
}
