package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    // Компонент, содержащий ключи/токены Telegram-бота
    private final TelegramCaloriesBotKeyComponents botKeyComponents;

    // Объект для работы с JSON
    private final ObjectMapper objectMapper;

    public boolean isValid(String authData) {
        try {
            // 1. Парсинг строки и декодирование значений
            Map<String, String> params = parseAuthData(authData);

            // Извлечение хэша из параметров
            String hash = params.remove("hash");
            if (hash == null) return false;

            // 2. Формирование строки для проверки (data_check_string)
            // Сортировка параметров по ключу обязательна
            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            byte[] secretKey;

            // 3. Генерация секретного ключа в зависимости от типа авторизации
            // Если есть параметр "user" (содержащий JSON), это Mini App (initData).
            // В виджете авторизации сайта данные пользователя (id, first_name) лежат прямо в корне.
            if (params.containsKey("user")) {
                // Это Mini App
                secretKey = hmacSha256(
                        botKeyComponents.getKey().getBytes(StandardCharsets.UTF_8),
                        "WebAppData"
                );
            } else {
                // Это Web-сайт (Telegram Login Widget)
                // Для виджета ключ — это стандартный SHA-256 хэш от токена бота
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                secretKey = digest.digest(botKeyComponents.getKey().getBytes(StandardCharsets.UTF_8));
            }

            // 4. Вычисление контрольного хэша
            String calculatedHash = bytesToHex(
                    hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey)
            );

            // Сравнение вычисленного хэша с переданным
            return calculatedHash.equals(hash);

        } catch (Exception e) {
            // Логирование ошибки и возврат false при исключении
            log.error("Telegram auth validation failed", e);
            return false;
        }
    }

    public Long extractTelegramId(String authData) {
        try {
            Map<String, String> params = parseAuthData(authData);

            // Проверяем, есть ли поле user (Mini App)
            String userJson = params.get("user");
            if (userJson != null) {
                // Читаем JSON и достаем только ID
                JsonNode node = objectMapper.readTree(userJson);
                return node.get("id").asLong();
            }

            // Если поля user нет, значит это виджет авторизации (Web-сайт), там id лежит прямо в корне
            String idStr = params.get("id");
            if (idStr != null) {
                return Long.parseLong(idStr);
            }

            return null;
        } catch (Exception e) {
            log.error("Ошибка при извлечении Telegram ID", e);
            return null;
        }
    }

    private Map<String, String> parseAuthData(String authData) {
        // Разбор строки формата key=value&key=value
        return Arrays.stream(authData.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        p -> p[0],
                        p -> p.length > 1 ? URLDecoder.decode(p[1], StandardCharsets.UTF_8) : ""
                ));
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        // Вычисление HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private byte[] hmacSha256(byte[] data, String key) throws Exception {
        // Перегруженный метод для удобства использования строки в качестве ключа
        return hmacSha256(data, key.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        // Преобразование массива байтов в шестнадцатеричную строку
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}