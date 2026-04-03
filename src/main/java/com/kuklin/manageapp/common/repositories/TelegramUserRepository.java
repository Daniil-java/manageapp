package com.kuklin.manageapp.common.repositories;

import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
    Optional<TelegramUser> findTelegramUserByBotIdentifierAndTelegramId(BotIdentifier botIdentifier, Long telegramId);
    List<TelegramUser> findAllByBotIdentifier(BotIdentifier botIdentifier);
    List<TelegramUser> findAllByBotIdentifierAndIsBotBlockedFalse(BotIdentifier botIdentifier);

}
