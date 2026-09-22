package com.kuklin.manageapp.bots.bookingbot.repositories;

import com.kuklin.manageapp.bots.bookingbot.entities.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
    Optional<ConversationState> findByTelegramId(Long telegramId);
}
