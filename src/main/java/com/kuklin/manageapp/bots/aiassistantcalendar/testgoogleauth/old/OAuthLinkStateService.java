//package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.old;
//
//import lombok.Getter;
//import org.springframework.core.env.Environment;
//import org.springframework.stereotype.Service;
//
//import java.security.MessageDigest;
//import java.security.SecureRandom;
//import java.time.Instant;
//import java.util.Base64;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * Держим одноразовые link'и и state+PKCE в памяти.
// * Никакой БД — всё исчезнет при рестарте (для теста ок).
// */
//@Service
//public class OAuthLinkStateService {
//
//    private static final long TTL_SECONDS = 15 * 60; // срок жизни link/state
//
//    // link -> chatId, expireAt
//    private final Map<String, LinkItem> linkMap = new ConcurrentHashMap<>();
//    // state -> chatId, verifier (для PKCE), expireAt
//    private final Map<String, StateItem> stateMap = new ConcurrentHashMap<>();
//
//    @Getter
//    private final String baseUrl;
//    @Getter
//    private final String clientId;
//    @Getter
//    private final String clientSecret;
//    @Getter
//    private final String redirectUri;
//
//    public OAuthLinkStateService(Environment environment) {
//        this.baseUrl = "https://kuklin.dev";
//        this.clientId = "1054896629464-fqd0bnpb5uao8f0khphkvd84i443q8jg.apps.googleusercontent.com";
//        this.clientSecret = environment.getProperty("GOOGLE_CALENDAR_KEY");
//        this.redirectUri = "https://kuklin.dev/auth/google/callback";
//    }
//
//    /** Одноразовая ссылка: UUID -> chatId (удаляем при использовании) */
//    public String issueOneTimeLink(long chatId) {
//        String id = UUID.randomUUID().toString();
//        linkMap.put(id, new LinkItem(chatId, Instant.now().plusSeconds(TTL_SECONDS)));
//        return id;
//    }
//
//    /** Достаём chatId по link и сразу удаляем — одноразово */
//    public long resolveChatIdByLink(String link) {
//        var item = linkMap.remove(link);
//        if (item == null || Instant.now().isAfter(item.expireAt)) {
//            throw new IllegalStateException("Link expired or invalid");
//        }
//        return item.chatId;
//    }
//
//    /** Генерим state + PKCE (verifier/challenge), держим в памяти до колбэка */
//    public PkceState issueState(long chatId) {
//        String state = UUID.randomUUID().toString();
//        String verifier = randomVerifier();
//        String challenge = s256(verifier);
//        stateMap.put(state, new StateItem(chatId, verifier, Instant.now().plusSeconds(TTL_SECONDS)));
//        return new PkceState(state, verifier, challenge);
//    }
//
//    /** Проверяем state, вытаскиваем chatId и code_verifier; state одноразовый — вычищаем */
//    public VerifiedState consumeState(String state) {
//        var item = stateMap.remove(state);
//        if (item == null || Instant.now().isAfter(item.expireAt)) {
//            throw new IllegalStateException("State expired or invalid");
//        }
//        return new VerifiedState(item.chatId, item.verifier);
//    }
//
//    // ===== PKCE utils =====
//    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
//
//    private static String randomVerifier() {
//        var rnd = new SecureRandom();
//        var sb = new StringBuilder();
//        for (int i = 0; i < 64; i++) sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
//        return sb.toString();
//    }
//
//    private static String s256(String verifier) {
//        try {
//            var md = MessageDigest.getInstance("SHA-256");
//            var digest = md.digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
//            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
//        } catch (Exception e) { throw new RuntimeException(e); }
//    }
//
//    // ===== маленькие записи =====
//    private record LinkItem(long chatId, Instant expireAt) {}
//    private record StateItem(long chatId, String verifier, Instant expireAt) {}
//
//    /** Что отдаём наружу */
//    public record PkceState(String state, String verifier, String challenge) {}
//    public record VerifiedState(long chatId, String verifier) {}
//}