package com.kuklin.manageapp.bots.pomidorotimer.repositories;

import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PomidoroUserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findUserByChatId(Long chatId);
    Optional<UserEntity> findUserEntityByTelegramId(Long telegramId);
}
