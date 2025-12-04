package com.kuklin.manageapp.payment.repositories;

import com.kuklin.manageapp.payment.entities.GenerationBalance;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenerationBalanceRepository extends JpaRepository<GenerationBalance, Long> {
    Optional<GenerationBalance> findByTelegramIdAndBotIdentifier(Long telegramId, BotIdentifier botIdentifier);
}
