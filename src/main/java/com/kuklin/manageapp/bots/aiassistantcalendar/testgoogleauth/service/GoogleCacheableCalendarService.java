package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.service;

import com.google.api.services.calendar.model.CalendarListEntry;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.entities.GoogleCacheableCalendar;
import com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.repositories.GoogleCacheableCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCacheableCalendarService {
    private final GoogleCacheableCalendarRepository repository;

    public GoogleCacheableCalendar findCalendarByIdAndTelegramIdOrNull(Long id, Long telegramId) {
        return repository.findByIdAndTelegramId(id, telegramId)
                .orElse(null);
    }

    @Transactional
    public void saveListOfCalendarsAndRemoveAllOfAnother(List<CalendarListEntry> list, Long telegramId) {
        List<GoogleCacheableCalendar> cacheableCalendars = new ArrayList<>();

        log.info("saveListOfCalendarsAndRemoveAllOfAnother start");
        for (CalendarListEntry entry: list) {
            cacheableCalendars.add(
                    new GoogleCacheableCalendar()
                            .setCalendarId(entry.getId())
                            .setTelegramId(telegramId)
                            .setSummary(entry.getSummary())
            );
        }
        repository.deleteAllByTelegramId(telegramId);
        repository.saveAll(cacheableCalendars);
    }

    public List<GoogleCacheableCalendar> findAllByTelegramId(Long telegramId) {
        return repository.findAllByTelegramId(telegramId);
    }
}
