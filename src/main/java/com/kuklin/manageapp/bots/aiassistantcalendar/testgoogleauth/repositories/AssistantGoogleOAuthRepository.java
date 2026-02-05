package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.AssistantGoogleOAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssistantGoogleOAuthRepository extends JpaRepository<AssistantGoogleOAuth, Long> {
    Optional<AssistantGoogleOAuth> findByTelegramId(Long telegramId);
}
