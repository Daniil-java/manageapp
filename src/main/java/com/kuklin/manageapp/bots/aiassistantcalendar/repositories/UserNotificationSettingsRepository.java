package com.kuklin.manageapp.bots.aiassistantcalendar.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettings, Long> {
    Optional<UserNotificationSettings> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    List<UserNotificationSettings> findAllByDailyEnabledTrue();
}
