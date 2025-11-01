package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.CodeVerifierUtil;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler.AssistantCalendarChooseUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private final GoogleOAuthProperties props;
    private final LinkStateService linkState;
    private final GoogleOAuthHttpClient google;
    private final TokenService tokenService;
    private final AssistantCalendarChooseUpdateHandler handler;

    public String start(UUID linkId) {
        try {
            log.info("5");
            var consumed = linkState.consumeLinkAndMakeState(linkId);
            var state = consumed.state();        // UUID
            var verifier = consumed.verifier();  // PKCE verifier
            var challenge = CodeVerifierUtil.toS256Challenge(verifier);

            log.info("11");
            String scope = String.join(" ", props.getScopes());

            // Собираем параметры
            var p = new LinkedHashMap<String, String>();
            p.put("response_type", "code");
            p.put("client_id", props.getClientId());
            p.put("redirect_uri", props.getRedirectUri());
            p.put("scope", scope);
            p.put("state", state.toString());
            p.put("code_challenge", challenge);
            p.put("code_challenge_method", "S256");
            p.put("access_type", "offline");
            p.put("include_granted_scopes", "true");
            p.put("prompt", "consent");

            String authUrl = buildUrl(props.getAuthUri(), p);

            log.info("12");
            // Лог — самое важное для 400 на шаге авторизации
            log.info("OAUTH START telegram_id={} state={} clientId={} redirectUri={} authUrl={}",
                    consumed.telegramId(),
                    p.get("state"),
                    mask(props.getClientId()),
                    props.getRedirectUri(),
                    authUrl);

            return authUrl;
        } catch (IllegalStateException ex) {
            log.warn("Invalid link {}: {}", linkId, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link is invalid or expired");
        }

    }

    public ResponseEntity<?> callback(String code, UUID state, String error, String errorDescription) {

        log.info("OAUTH CALLBACK state={} codePresent={} error={}",
                state, code != null, error);
        log.info("14");

        // 0) Если Google вернул ошибку вместо кода — отдадим ясный ответ
        if (error != null) {
            // state все равно «поглотим», чтобы его нельзя было переиспользовать
            try {
                linkState.consumeState(state);
            } catch (Exception ignore) {
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "error", error,
                    "error_description", errorDescription != null ? errorDescription : ""
            ));
        }

        log.info("15");
        // 1) Проверяем и "поглощаем" state (получаем telegramId + verifier)
        LinkStateService.CallbackState cb;
        try {
            cb = linkState.consumeState(state);
        } catch (Exception e) {
            log.error("OAUTH CALLBACK state consume failed: state={}, msg={}, class={}",
                    state, e.getMessage(), e.getClass().getSimpleName(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "stage", "consume_state",
                    "message", e.getMessage()
            ));
        }

        log.info("16");
        try {
            // 2) Обмен кода на токены
            GoogleOAuthHttpClient.TokenResponse tokens = google.exchangeCode(code, cb.verifier());

            log.info("17");
            // 3) Юзер-инфо по access_token
            GoogleOAuthHttpClient.UserInfo userInfo = google.getUserInfo(tokens.access_token());

            log.info("18");
            // 4) Сохранение в БД
            AssistantGoogleOAuth auth = tokenService
                    .saveFromAuthCallback(cb.telegramId(), tokens, userInfo, Instant.now());

            log.info("19");
            // 5) Отправка уведомления пользователю
//            handler.handleGoogleCallback(auth);
            // 6) Успех (можно редиректнуть в tg: https://t.me/<bot>?start=connected)
            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "email", userInfo.email(),
                    "sub", userInfo.sub()
            ));
        } catch (RestClientResponseException ex) {
            // Здесь поймаем тело от Google (invalid_grant/redirect_uri_mismatch/etc)
            log.warn("OAUTH TOKEN EXCHANGE FAILED: status={} body={}", ex.getRawStatusCode(), ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getRawStatusCode()).body(Map.of(
                    "status", "error",
                    "google_status", ex.getRawStatusCode(),
                    "google_body", ex.getResponseBodyAsString()
            ));
        }
    }

    private static String buildUrl(String base, Map<String, String> params) {
        var q = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        return base + "?" + q;
    }

    private static String mask(String s) {
        return (s == null) ? "null" : s.replaceAll("(^.{6}).*(.{6}$)", "$1...$2");
    }
}
