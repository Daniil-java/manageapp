package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;

/**
 * Две ручки:
 *  - /auth/google/start   — редиректим на Google OAuth с PKCE
 *  - /auth/google/callback — принимаем code и обмениваем на токены (держим только в памяти)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final OAuthLinkStateService linkState;
    private final InMemoryUserTokens tokens;                 // "хранилище" в памяти
    private final AssistantTelegramBot assistantBot;          // твой бот
    private final RestTemplate http = new RestTemplate();

    @GetMapping("/auth/google/start")
    public ResponseEntity<Void> start(@RequestParam String link) {
        // Связываем браузерную сессию с конкретным chatId
        long chatId = linkState.resolveChatIdByLink(link);

        // Генерим state + PKCE
        var pk = linkState.issueState(chatId);

        // Собираем URL авторизации Google
        String url = UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", linkState.getClientId())
                .queryParam("redirect_uri", linkState.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "https://www.googleapis.com/auth/calendar")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", pk.state())
                .queryParam("code_challenge", pk.challenge())
                .queryParam("code_challenge_method", "S256")
                .build(true)
                .toUriString();

        // 302 на Google
        HttpHeaders h = new HttpHeaders();
        h.setLocation(java.net.URI.create(url));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }

    @GetMapping("/auth/google/callback")
    public String callback(@RequestParam String code, @RequestParam String state) {
        // Валидация state, получаем chatId и code_verifier
        var verified = linkState.consumeState(state);

        // Меняем code на токены
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", linkState.getRedirectUri());
        form.add("client_id", linkState.getClientId());
        form.add("client_secret", linkState.getClientSecret());
        form.add("code_verifier", verified.verifier());

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        var req = new HttpEntity<>(form, headers);

        var resp = http.postForEntity("https://oauth2.googleapis.com/token", req, TokenResponse.class);
        var body = resp.getBody();
        if (body == null || body.access_token() == null) {
            assistantBot.sendReturnedMessage(verified.chatId(), "❌ Ошибка Google OAuth. Попробуй ещё раз: /auth");
            return "OAuth error. You may close this tab.";
        }

        // Сохраняем токены в памяти (НЕ в БД) — хватит для тестов до рестарта
        tokens.save(verified.chatId(), body, Instant.now());

        // Уведомляем прямо в чат
        assistantBot.sendReturnedMessage(verified.chatId(), "✅ Google подключён. Теперь присылай текст/голос — добавлю в календарь.");

        // Простой ответ в браузере
        return "Готово. Можешь вернуться в Telegram.";
    }

    /** DTO под ответ Google /token */
    public record TokenResponse(
            String access_token,
            String refresh_token,
            Long   expires_in,
            String scope,
            String token_type
    ) {}
}
