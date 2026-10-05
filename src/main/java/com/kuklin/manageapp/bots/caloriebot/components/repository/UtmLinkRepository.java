package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UtmLinkRepository extends JpaRepository<UtmLink, Long> {
    Optional<UtmLink> findByCode(String code);
}
