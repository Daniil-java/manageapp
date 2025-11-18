package com.kuklin.manageapp.bots.aiassistantcalendar.repositories;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserGoogleCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGoogleCalendarRepository extends JpaRepository<UserGoogleCalendar, Long> {
}
