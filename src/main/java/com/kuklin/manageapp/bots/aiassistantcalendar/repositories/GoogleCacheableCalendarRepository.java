package com.kuklin.manageapp.bots.aiassistantcalendar.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.GoogleCacheableCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleCacheableCalendarRepository extends JpaRepository<GoogleCacheableCalendar, Long> {
    Optional<GoogleCacheableCalendar> findByIdAndTelegramId(Long id, Long telegramId);

    List<GoogleCacheableCalendar> findAllByTelegramId(Long telegramId);

    void deleteAllByTelegramId(Long telegramId);
}
