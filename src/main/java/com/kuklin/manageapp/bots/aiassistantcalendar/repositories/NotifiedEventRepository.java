package com.kuklin.manageapp.bots.aiassistantcalendar.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.NotifiedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface NotifiedEventRepository extends JpaRepository<NotifiedEvent, Long> {
    boolean existsByCalendarIdAndEventIdAndUserId(String calendarId, String eventId, Long userId);

    /**
     * Удалить все записи, у которых notifiedAt < cutoff.
     * Возвращает количество удалённых строк.
     */
    @Modifying
    @Query("DELETE FROM NotifiedEvent ne WHERE ne.notifiedAt < :cutoff")
    int deleteAllByNotifiedAtBefore(@Param("cutoff") Instant cutoff);

    boolean existsByCalendarIdAndEventIdAndUserIdAndReminderMinutes(
            String calendarId,
            String eventId,
            Long userId,
            Integer reminderMinutes
    );
}