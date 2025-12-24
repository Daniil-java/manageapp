package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserGoogleCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.UserGoogleCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserGoogleCalendarService {
    private final UserGoogleCalendarRepository repository;

    public String getUserCalendarIdByTelegramIdOrNull(Long telegramId) {
        Optional<UserGoogleCalendar> optional =
                repository.findById(telegramId);

        if (optional.isEmpty()) return null;
        else return optional.get().getCalendarId();
    }

    public UserGoogleCalendar setCalendarIdByTelegramId(Long telegramId, String calendarId) {
        Optional<UserGoogleCalendar> optional =
                repository.findById(telegramId);

        if (optional.isEmpty()) {
            return repository.save(
                    new UserGoogleCalendar()
                            .setTelegramId(telegramId)
                            .setCalendarId(calendarId)
            );
        } else {
            return repository.save(
                    optional.get()
                            .setCalendarId(calendarId)
            );
        }
    }

    public List<UserGoogleCalendar> findAll() {
        return repository.findAll();
    }
}
