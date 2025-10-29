package com.kuklin.manageapp.bots.kworkparser.repositories;

import com.kuklin.manageapp.bots.kworkparser.entities.UserUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserUrlRepository extends JpaRepository<UserUrl, Long> {
    Optional<UserUrl> findUserUrlByUrlIdAndTelegramId(Long urlId, Long telegramId);
    List<UserUrl> findAllByUrlId(Long urlId);
}
