package com.kuklin.manageapp.common.auth.security;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Определяет IP клиента для лимитов входа ({@link AuthRateLimiter}).
 * Кто вызывает: {@link AuthController} — в /auth/register, /auth/login, /auth/telegram.
 * <p>
 * Проблема: бэк на Railway, запросы приходят через их edge-прокси. request.getRemoteAddr() возвращает
 * адрес прокси (100.x) — одинаковый для всех пользователей. Если считать лимит по нему, все пользователи
 * делят один счётчик: 5 регистраций в час на весь сайт.
 * <p>
 * Решение: настоящий IP прокси Railway передаёт в заголовках:
 * <ul>
 * <li>X-Real-IP — Railway перезаписывает его настоящим IP, даже если клиент прислал свой;</li>
 * <li>X-Forwarded-For — присланный клиентом Railway срезает, первым адресом ставит настоящий IP.</li>
 * </ul>
 * Но заголовок может прислать кто угодно. Поэтому верим им, только если сам запрос пришёл от прокси —
 * с внутреннего адреса. Если запрос пришёл напрямую с внешнего адреса, заголовки игнорируем:
 * иначе клиент подставлял бы случайный IP в каждом запросе и обходил лимит.
 * <p>
 * Не глобальный server.forward-headers-strategy намеренно: он меняет ещё и схему / хост запроса,
 * а это затронуло бы DomainUrlFilter и редиректы.
 */
public final class ClientIpResolver {

    // Утилитный класс — только статические методы
    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // 1. Запрос пришёл не от прокси — это и есть клиент, заголовкам не верим
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. От прокси — берём X-Real-IP. Бывает, что Railway кладёт туда адрес своей инфраструктуры
        // (тоже 100.x) — тогда он бесполезен, идём в X-Forwarded-For
        String realIp = trimToNull(request.getHeader("X-Real-IP"));
        if (realIp != null && !isTrustedProxy(realIp)) {
            return realIp;
        }

        // 3. X-Forwarded-For: "клиент, прокси1, прокси2". Берём первый внешний адрес:
        // Railway срезает присланный клиентом заголовок и ставит его IP первым
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            for (String part : forwardedFor.split(",")) {
                String ip = trimToNull(part);
                if (ip != null && !isTrustedProxy(ip)) {
                    return ip;
                }
            }
        }

        // 4. Настоящий IP не нашли — лучше то, что есть (лимит станет общим для всех за этим адресом)
        return realIp != null ? realIp : remoteAddr;
    }

    /**
     * Внутренний адрес — значит прокси, а не клиент:
     * 127.x, ::1 (localhost — локальная разработка), 10.x, 172.16–31.x, 192.168.x (частные сети),
     * 100.x (edge-прокси Railway), fc00::/7 (частные IPv6).
     * Package-private — для тестов.
     */
    static boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank()) return false;
        // Только IP-литералы (цифры, точки, двоеточия): getByName с именем хоста пошёл бы в DNS
        if (!address.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress inet = InetAddress.getByName(address);
            // loopback — 127.x и ::1; siteLocal — 10.x, 172.16–31.x, 192.168.x
            if (inet.isLoopbackAddress() || inet.isSiteLocalAddress()) return true;
            byte[] bytes = inet.getAddress();
            if (bytes.length == 4) return (bytes[0] & 0xFF) == 100;  // edge-прокси Railway — всегда 100.0.0.0/8
            return (bytes[0] & 0xFE) == 0xFC;                       // IPv6 unique local (fc00::/7)
        } catch (UnknownHostException e) {
            // Строка похожа на IP, но не разбирается (например, "1.2.3") — не доверяем
            return false;
        }
    }

    /** Пустая строка или пробелы — то же, что заголовка нет. */
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
