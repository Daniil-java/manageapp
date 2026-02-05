package com.kuklin.manageapp.bots.payment.repositories;

import com.kuklin.manageapp.bots.payment.entities.GenerationBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GenerationBalanceRepository extends JpaRepository<GenerationBalance, Long> {
    Optional<GenerationBalance> findByTelegramId(Long telegramId);
}
