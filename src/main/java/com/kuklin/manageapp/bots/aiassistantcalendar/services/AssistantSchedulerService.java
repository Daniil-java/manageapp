package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.processors.EventNotificationNoteCleanTableScheduleProcessor;
import com.kuklin.manageapp.bots.aiassistantcalendar.services.processors.EventNotificationSchedulerProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class AssistantSchedulerService {

    private final EventNotificationSchedulerProcessor eventNotificationSchedulerProcessor;
    private final EventNotificationNoteCleanTableScheduleProcessor eventNotificationNoteCleanTableScheduleProcessor;

    @Scheduled(cron = "0 */1 * * * *")
    public void eventNotificationSchedulerProcess() {
        getInfo(eventNotificationSchedulerProcessor.getSchedulerName());
        eventNotificationSchedulerProcessor.process();
    }

    @Scheduled(cron = "0 */2 * * * *")
    public void eventNotificationNoteCleanTableScheduleProcess() {
        getInfo(eventNotificationNoteCleanTableScheduleProcessor.getSchedulerName());
        eventNotificationNoteCleanTableScheduleProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
