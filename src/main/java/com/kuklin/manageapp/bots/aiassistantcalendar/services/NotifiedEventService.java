package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.NotifiedEvent;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.NotifiedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotifiedEventService {

    private final NotifiedEventRepository repo;

    /**
     * Провеяем уведомляли ли мы пользователя, об определенной задаче
     * @return true - уведомляли, false - НЕ уведомляли
     */
    public boolean isNotified(String calendarId, String eventId, Long userId) {
        return repo.existsByCalendarIdAndEventIdAndUserId(calendarId, eventId, userId);
    }

    /**
     * Помечает мероприятие пользователя, в определенном календаре, как уже напомненное пользователю
     * @return запись о нотификации
     */
    public NotifiedEvent markAsNotified(String calendarId, String eventId, Long userId) {
        return repo.save(
                new NotifiedEvent()
                        .setUserId(userId)
                        .setCalendarId(calendarId)
                        .setEventId(eventId)
        );
    }

    /**
     * Удаляет записи старше `hours` часов.
     * @return количество удалённых записей
     */
    @Transactional
    public int cleanOlderThanHours(long hours) {
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        int deleted = repo.deleteAllByNotifiedAtBefore(cutoff);
        // логирование для мониторинга
        log.info("NotifiedEventCleanup: removed {} rows older than {} hours (cutoff={})", deleted, hours, cutoff);
        return deleted;
    }

}
