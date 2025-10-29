package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.controller;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.CodeVerifierUtil;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations.GoogleOAuthProperties;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.GoogleOAuthHttpClient;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.LinkStateService;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final GoogleOAuthProperties props;
    private final LinkStateService linkState;
    private final GoogleOAuthHttpClient google;
    private final TokenService tokenService;

    /** Пример: бот шлет пользователю ссылку: https://your.app.com/auth/google/start?linkId=<UUID> */
    @GetMapping("/start")
    public ResponseEntity<Void> start(@RequestParam("linkId") UUID linkId) {
        var consumed = linkState.consumeLinkAndMakeState(linkId);
        var state = consumed.state();      // UUID
        var verifier = consumed.verifier();// PKCE verifier

        var challenge = CodeVerifierUtil.toS256Challenge(verifier);

        // Схлопываем в URL авторизации
        String scope = String.join(" ", props.getScopes());
        var uri = URI.create(props.getAuthUri()
                + "?response_type=code"
                + "&client_id=" + url(props.getClientId())
                + "&redirect_uri=" + url(props.getRedirectUri())
                + "&scope=" + url(scope)
                + "&state=" + url(state.toString())
                + "&code_challenge=" + url(challenge)
                + "&code_challenge_method=S256"
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=consent" // чтобы получить refresh_token стабильно
        );

        // Версификатор (verifier) хранится в БД в OAuthState (мы сохранили его там)
        return ResponseEntity.status(302).location(uri).build();
    }

    /** Колбэк с code+state от Google */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam("code") String code,
                                      @RequestParam("state") UUID state) {
        // 1) Проверяем и "поглощаем" state (получаем chatId + verifier)
        var cb = linkState.consumeState(state);

        // 2) Обмениваем code на токены
        var tokens = google.exchangeCode(code, cb.verifier());

        // 3) Юзер-инфо по access_token
        var userInfo = google.getUserInfo(tokens.access_token());

        // 4) Сохраняем все в БД (refresh шифруем)
        tokenService.saveFromAuthCallback(cb.chatId(), tokens, userInfo, Instant.now());

        // 5) Можно показать пользователю HTML "Успешно" или сделать редирект в Телеграм-диплинк
        return ResponseEntity.ok(Map.of(
                "status", "connected",
                "email", userInfo.email(),
                "sub", userInfo.sub()
        ));
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
