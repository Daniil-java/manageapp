package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramAuthServiceTest {

    private static final String BOT_TOKEN = "123456:TEST_BOT_TOKEN";
    private static final String MINI_APP_TOKEN = "654321:TEST_MINI_APP_TOKEN";

    private TelegramAuthService service;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("CALORY_BOT_TOKEN", BOT_TOKEN)
                .withProperty("CALORY_BOT_MINI_APP_TOKEN", MINI_APP_TOKEN);
        service = new TelegramAuthService(new TelegramCaloriesBotKeyComponents(env), new ObjectMapper());
        ReflectionTestUtils.setField(service, "widgetMaxAge", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "initDataMaxAge", Duration.ofHours(24));
    }

    // ── Telegram Login Widget ──────────────────────────────

    @Test
    void acceptsFreshSignedData() {
        assertTrue(service.isValidLoginWidget(signedWidget(widgetData(Instant.now()))));
    }

    @Test
    void rejectsTamperedData() {
        Map<String, String> data = signedWidget(widgetData(Instant.now()));
        data.put("id", "999");
        assertFalse(service.isValidLoginWidget(data));
    }

    @Test
    void rejectsExpiredData() {
        assertFalse(service.isValidLoginWidget(signedWidget(widgetData(Instant.now().minus(Duration.ofHours(25))))));
    }

    @Test
    void rejectsMissingHash() {
        assertFalse(service.isValidLoginWidget(widgetData(Instant.now())));
    }

    // ── initData мини-аппы ─────────────────────────────────

    @Test
    void acceptsFreshInitData() {
        assertTrue(service.isValid(signedInitData(initData(Instant.now()))));
    }

    @Test
    void rejectsExpiredInitData() {
        assertFalse(service.isValid(signedInitData(initData(Instant.now().minus(Duration.ofHours(25))))));
    }

    @Test
    void rejectsInitDataWithoutAuthDate() {
        Map<String, String> data = initData(Instant.now());
        data.remove("auth_date");
        assertFalse(service.isValid(signedInitData(data)));
    }

    private Map<String, String> widgetData(Instant authDate) {
        Map<String, String> data = new HashMap<>();
        data.put("id", "42");
        data.put("first_name", "Иван");
        data.put("username", "ivan");
        data.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        return data;
    }

    private Map<String, String> initData(Instant authDate) {
        Map<String, String> data = new HashMap<>();
        data.put("user", "{\"id\":42,\"first_name\":\"Иван\"}");
        data.put("query_id", "AAE");
        data.put("auth_date", String.valueOf(authDate.getEpochSecond()));
        return data;
    }

    // Подпись виджета: HMAC-SHA256(data_check_string, SHA256(bot_token))
    private Map<String, String> signedWidget(Map<String, String> data) {
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256").digest(BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
            data.put("hash", hmacHex(secret, dataCheckString(data)));
            return data;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Подпись мини-аппы: HMAC-SHA256(data_check_string, HMAC-SHA256(bot_token, "WebAppData")),
    // результат — строка как в заголовке X-TG-INIT-DATA
    private String signedInitData(Map<String, String> data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secret = mac.doFinal(MINI_APP_TOKEN.getBytes(StandardCharsets.UTF_8));
            data.put("hash", hmacHex(secret, dataCheckString(data)));
            return data.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String dataCheckString(Map<String, String> data) {
        return data.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String hmacHex(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
