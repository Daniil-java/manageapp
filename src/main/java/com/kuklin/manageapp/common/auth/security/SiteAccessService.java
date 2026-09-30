package com.kuklin.manageapp.common.auth.security;

import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Закрытый доступ к сайту на время тестирования: JWT выдаётся только тем,
 * чей email или telegramId есть в списке разрешённых (переменные окружения, без БД).
 * Мини-аппу не затрагивает — там авторизация через initData, без /auth/*.
 * <p>
 * Чтобы открыть сайт для всех — SITE_ACCESS_RESTRICTED=false.
 */
@Service
@Slf4j
public class SiteAccessService {

    private final boolean restricted;
    private final Set<String> allowedEmails;
    private final Set<Long> allowedTelegramIds;

    public SiteAccessService(
            // По умолчанию закрыто: если списки забыли задать, не войдёт никто, а не все подряд
            @Value("${site.access.restricted:true}") boolean restricted,
            @Value("${site.access.allowed-emails:}") String allowedEmails,
            @Value("${site.access.allowed-telegram-ids:}") String allowedTelegramIds) {
        this.restricted = restricted;
        this.allowedEmails = split(allowedEmails).stream()
                .map(SiteAccessService::normalizeEmail)
                .collect(Collectors.toSet());
        this.allowedTelegramIds = split(allowedTelegramIds).stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        log.info("Site access: restricted={}, allowed emails={}, allowed telegram ids={}",
                restricted, this.allowedEmails.size(), this.allowedTelegramIds.size());
    }

    public void checkEmail(String email) {
        if (restricted && (email == null || !allowedEmails.contains(normalizeEmail(email)))) {
            throw new ErrorResponseException(ErrorStatus.SITE_ACCESS_DENIED);
        }
    }

    public void checkTelegramId(Long telegramId) {
        if (restricted && !allowedTelegramIds.contains(telegramId)) {
            throw new ErrorResponseException(ErrorStatus.SITE_ACCESS_DENIED);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> split(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
