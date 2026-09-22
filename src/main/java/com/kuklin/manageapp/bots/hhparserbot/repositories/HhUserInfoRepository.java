package com.kuklin.manageapp.bots.hhparserbot.repositories;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HhUserInfoRepository extends JpaRepository<HhUserInfo, Long> {
    Optional<HhUserInfo> findByTelegramId(Long telegramId);
}
