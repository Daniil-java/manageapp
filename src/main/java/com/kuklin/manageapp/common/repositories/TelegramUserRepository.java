package com.kuklin.manageapp.common.repositories;

import com.kuklin.manageapp.common.entities.TelegramUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramUserRepository extends JpaRepository<TelegramUser, Long> {
}
