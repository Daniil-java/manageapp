package com.kuklin.manageapp.bots.kworkparser.repositories;

import com.kuklin.manageapp.bots.kworkparser.entities.UserKworkNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserKworkNotificationRepository extends JpaRepository<UserKworkNotification, Long> {
    Optional<UserKworkNotification> findUserKworkNotificationByKworkIdAndTelegramIdAndStatus(Long kworkId, Long telegramId, UserKworkNotification.Status status);
}
