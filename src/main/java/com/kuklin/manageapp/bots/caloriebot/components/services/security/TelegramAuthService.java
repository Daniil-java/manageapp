package com.kuklin.manageapp.bots.caloriebot.components.services.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
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

    // Подпись Telegram не протухает — без проверки auth_date однажды перехваченные данные
    // действуют вечно. Поэтому ограничиваем их возраст.

    // Данные Telegram Login Widget меняют на JWT сразу после входа
    @Value("${telegram.auth.widget-max-age:PT24H}")
    private Duration widgetMaxAge;

    // initData мини-аппы один и тот же всю сессию; перезапуск мини-аппы выдаёт свежий
    @Value("${telegram.auth.init-data-max-age:PT24H}")
    private Duration initDataMaxAge;

    /**
     * Проверка initData мини-аппы (заголовок X-TG-INIT-DATA): подпись + свежесть auth_date.
     */
    public boolean isValid(String authData) {
        // Парсинг строки и декодирование значений
        Map<String, String> params = parseAuthData(authData);
        return isValid(params) && isFresh(params, initDataMaxAge);
    }

    /**
     * Проверка данных Telegram Login Widget (объект из data-onauth) для входа на сайте:
     * подпись + свежесть auth_date.
     */
    public boolean isValidLoginWidget(Map<String, String> widgetData) {
        // Виджет не присылает "user" — но если его подложить, isValid() проверит подпись
        // ключом мини-аппы, а не бота. Такие данные виджетом быть не могут.
        return !widgetData.containsKey("user")
                && isValid(widgetData)
                && isFresh(widgetData, widgetMaxAge);
    }

    private boolean isFresh(Map<String, String> params, Duration maxAge) {
        try {
            long authDate = Long.parseLong(params.get("auth_date"));
            Duration age = Duration.between(Instant.ofEpochSecond(authDate), Instant.now());
            if (age.compareTo(maxAge) > 0) {
                log.warn("Telegram auth_date is too old: {}", age);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            // parseLong(null) тоже бросает NumberFormatException
            log.warn("Telegram auth_date is missing or invalid: {}", params.get("auth_date"));
            return false;
        }
    }

    private boolean isValid(Map<String, String> authParams) {
        try {
            // Работаем с копией: hash удаляется из параметров
            Map<String, String> params = new HashMap<>(authParams);

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
                // Это Mini App (веб-версия zef-fe/zefir.fit) — открывается через ОТДЕЛЬНОГО бота,
                // поэтому подпись проверяем его токеном (miniAppKey), а не CALORY_BOT_TOKEN.
                secretKey = hmacSha256(
                        botKeyComponents.getMiniAppKey().getBytes(StandardCharsets.UTF_8),
                        "WebAppData"
                );
            } else {
                // Это Web-сайт (Telegram Login Widget)
                // Для виджета ключ — это стандартный SHA-256 хэш от токена бота
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                secretKey = digest.digest(botKeyComponents.getKey().getBytes(StandardCharsets.UTF_8));
            }

            // 4. Вычисление контрольного хэша
            byte[] calculatedHash = hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);
            byte[] expectedHash = hexToBytes(hash);

            // Сравнение вычисленного хэша с переданным — MessageDigest.isEqual сравнивает все байты
            // за constant time, в отличие от String.equals/Arrays.equals, которые останавливаются
            // на первом несовпадении.
            return MessageDigest.isEqual(calculatedHash, expectedHash);

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

    private byte[] hexToBytes(String hex) {
        // Преобразование hex-строки (присланного клиентом hash) обратно в массив байтов.
        // Нечётная длина/не-hex символы -> NumberFormatException, ловится в isValid() как невалидная подпись.
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Odd-length hex string: " + hex);
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}