package com.kuklin.manageapp.bots.aiassistantcalendar.services.processors;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.NotifiedEventService;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class EventNotificationNoteCleanTableScheduleProcessor implements ScheduleProcessor {
    private final NotifiedEventService notifiedEventService;
    private static final Integer HOUR = 2;
    @Override
    public void process() {
        notifiedEventService.cleanOlderThanHours(HOUR);
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
