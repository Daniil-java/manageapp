package com.kuklin.manageapp.common.repositories;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    Optional<TelegramUser> findTelegramUserByBotIdentifierAndTelegramId(BotIdentifier botIdentifier, Long telegramId);
    List<TelegramUser> findAllByBotIdentifier(BotIdentifier botIdentifier);
    List<TelegramUser> findAllByBotIdentifierAndIsBotBlockedFalse(BotIdentifier botIdentifier);
    Optional<TelegramUser> findTelegramUserByTelegramId(Long tgId);

    Optional<TelegramUser> findFirstByTelegramIdAndAppUserIdIsNotNull(Long tgId);

    Optional<TelegramUser> findFirstByTelegramId(Long tgId);
    Optional<TelegramUser> findTelegramUserByAppUserIdAndBotIdentifier(Long appUserId, BotIdentifier botIdentifier);

    // Атомарный инкремент в БД: не теряет параллельные инкременты и не трогает остальные колонки
    @Modifying
    @Query("update TelegramUser t set t.responseCount = coalesce(t.responseCount, 0) + 1 where t.id = :id")
    int incrementResponseCount(@Param("id") Long id);
}
