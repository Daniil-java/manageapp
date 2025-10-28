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

@Slf4j
@RestController
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final OAuthLinkStateService linkState;
    private final InMemoryUserTokens tokens;         // "хранилище" в памяти на тест
    private final AssistantTelegramBot assistantBot; // твой бот
    private final RestTemplate http = new RestTemplate();

    /**
     * Старт OAuth: по одноразовому link -> находим chatId, генерим state+PKCE и уводим на Google (302).
     */
    @GetMapping("/auth/google/start")
    public ResponseEntity<?> start(@RequestParam("link") String link) {
        try {
            long chatId = linkState.resolveChatIdByLink(link); // выбросит, если link протух/левый
            var pk = linkState.issueState(chatId);             // state + verifier/challenge

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

            HttpHeaders h = new HttpHeaders();
            h.setLocation(java.net.URI.create(url));
            return new ResponseEntity<>(h, HttpStatus.FOUND);  // мгновенный 302

        } catch (IllegalStateException badLink) {
            // без падения приложения — вернём 400 и понятный текст
            return ResponseEntity.badRequest().body("Link expired/invalid. Запусти /auth в боте ещё раз.");
        }
    }

    /**
     * Колбэк от Google: проверяем state, меняем code на токены, шлём “подключено” в чат.
     */
    @GetMapping("/auth/google/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        try {
            // Восстанавливаем chatId и code_verifier; state одноразовый
            var verified = linkState.consumeState(state);

            // Обмен кода на токены
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
                return ResponseEntity.badRequest().body("OAuth error. You may close this tab.");
            }

            // Сохраняем в памяти (до рестарта)
            tokens.save(verified.chatId(), body, Instant.now());

            // Сообщаем в чат
            assistantBot.sendReturnedMessage(verified.chatId(), "✅ Google подключён. Теперь присылай текст/голос — добавлю в календарь.");

            // Ответ в браузер
            return ResponseEntity.ok("Готово. Можешь вернуться в Telegram.");

        } catch (IllegalStateException badState) {
            return ResponseEntity.badRequest().body("State expired/invalid. Запусти /auth в боте ещё раз.");
        } catch (Exception e) {
            log.error("OAuth callback error", e);
            return ResponseEntity.status(500).body("Internal error. Check logs.");
        }
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
