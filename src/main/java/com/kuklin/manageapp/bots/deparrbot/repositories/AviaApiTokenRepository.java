package com.kuklin.manageapp.bots.deparrbot.repositories;

import com.kuklin.manageapp.bots.deparrbot.entities.AviaApiToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AviaApiTokenRepository extends JpaRepository<AviaApiToken, Long> {
    Optional<AviaApiToken> findByTokenHash(String tokenHash);

    Optional<AviaApiToken> findByTelegramId(Long telegramId);
}
