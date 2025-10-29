package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.controller;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.CodeVerifierUtil;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleOAuthHttpClient;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.LinkStateService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthController {

    private final GoogleOAuthProperties props;
    private final LinkStateService linkState;
    private final GoogleOAuthHttpClient google;
    private final TokenService tokenService;

    /**
     * Бот шлёт ссылку вида:
     *   https://<host>/auth/google/start?linkId=<UUID>
     * Для диагностики можно добавить &debug=true и получить JSON с параметрами вместо редиректа.
     */
    @GetMapping("/start")
    public ResponseEntity<?> start(@RequestParam("linkId") UUID linkId,
                                   @RequestParam(value = "debug", required = false, defaultValue = "false") boolean debug) {
        var consumed = linkState.consumeLinkAndMakeState(linkId);
        var state = consumed.state();        // UUID
        var verifier = consumed.verifier();  // PKCE verifier
        var challenge = CodeVerifierUtil.toS256Challenge(verifier);

        String scope = String.join(" ", props.getScopes());

        // Собираем параметры, чтобы и в логах, и в debug их видеть как есть
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

        // Лог — самое важное для 400 на шаге авторизации
        log.info("OAUTH START chatId={} state={} clientId={} redirectUri={} authUrl={}",
                consumed.chatId(),
                p.get("state"),
                mask(props.getClientId()),
                props.getRedirectUri(),
                authUrl);

        if (debug) {
            // Удобно открыть в браузере /auth/google/start?linkId=...&debug=true
            return ResponseEntity.ok(Map.of("params", p, "authUrl", authUrl));
        }
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    /**
     * Колбэк Google: может прийти как (code,state), так и (error,error_description,state).
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam(value = "code", required = false) String code,
                                      @RequestParam("state") UUID state,
                                      @RequestParam(value = "error", required = false) String error,
                                      @RequestParam(value = "error_description", required = false) String errorDescription) {
        log.info("OAUTH CALLBACK state={} codePresent={} error={}",
                state, code != null, error);

        // 0) Если Google вернул ошибку вместо кода — отдадим ясный ответ
        if (error != null) {
            // state все равно «поглотим», чтобы его нельзя было переиспользовать
            try { linkState.consumeState(state); } catch (Exception ignore) {}
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "error", error,
                    "error_description", errorDescription != null ? errorDescription : ""
            ));
        }

        // 1) Проверяем и "поглощаем" state (получаем chatId + verifier)
        var cb = linkState.consumeState(state);

        try {
            // 2) Обмен кода на токены
            var tokens = google.exchangeCode(code, cb.verifier());

            // 3) Юзер-инфо по access_token
            var userInfo = google.getUserInfo(tokens.access_token());

            // 4) Сохранение в БД
            tokenService.saveFromAuthCallback(cb.chatId(), tokens, userInfo, Instant.now());

            // 5) Успех (можно редиректнуть в tg: https://t.me/<bot>?start=connected)
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
