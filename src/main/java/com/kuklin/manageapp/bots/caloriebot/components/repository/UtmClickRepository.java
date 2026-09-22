package com.kuklin.manageapp.bots.caloriebot.components.repository;

import com.kuklin.manageapp.bots.caloriebot.entities.utm.UtmClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UtmClickRepository extends JpaRepository<UtmClick, Long> {
    long countByUtmLinkId(Long utmLinkId);
    boolean existsByUserId(Long userId);
}
