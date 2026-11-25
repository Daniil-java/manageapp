package com.kuklin.manageapp.bots.deparrbot.services;

import com.kuklin.manageapp.bots.deparrbot.entities.AviaApiToken;
import com.kuklin.manageapp.bots.deparrbot.repositories.AviaApiTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Личные токены для REST API авиабота. Токен показывается один раз в чате,
 * в БД лежит только его SHA-256. Новый /token отзывает старый.
 */
@Service
@RequiredArgsConstructor
public class AviaApiTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AviaApiTokenRepository aviaApiTokenRepository;

    @Transactional
    public String issueToken(Long telegramId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AviaApiToken entity = aviaApiTokenRepository.findByTelegramId(telegramId)
                .orElseGet(() -> new AviaApiToken().setTelegramId(telegramId));
        entity.setTokenHash(getHash(token));
        aviaApiTokenRepository.save(entity);
        return token;
    }

    public Long findTelegramIdOrNull(String token) {
        if (token == null || token.isBlank()) return null;
        return aviaApiTokenRepository.findByTokenHash(getHash(token.trim()))
                .map(AviaApiToken::getTelegramId)
                .orElse(null);
    }

    private String getHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
