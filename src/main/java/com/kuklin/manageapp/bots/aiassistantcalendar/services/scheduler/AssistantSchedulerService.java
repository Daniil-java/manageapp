package com.kuklin.manageapp.bots.aiassistantcalendar.services.scheduler;

import com.kuklin.manageapp.common.scheduler.BotScheduler;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
@BotScheduler(BotIdentifier.ASSISTANT_BOT)
public class AssistantSchedulerService {

    private final EventNotificationSchedulerProcessor eventNotificationSchedulerProcessor;
    private final EventNotificationNoteCleanTableScheduleProcessor eventNotificationNoteCleanTableScheduleProcessor;
    private final DailyNotificationSchedulerProcessor dailyNotificationSchedulerProcessor;
    private final UserAuthNotificationSchedulerProcessor userAuthNotificationSchedulerProcessor;

    @Scheduled(cron = "0 */5 * * * *")
    public void eventNotificationSchedulerProcess() {
        getInfo(eventNotificationSchedulerProcessor.getSchedulerName());
        eventNotificationSchedulerProcessor.process();
    }

    // Раз в сутки в 00:00: чистит отметки старше 72 ч — чаще не нужно.
    // Было "0 0 */30 * * *" — выглядит как «каждые 30 минут», а значит то же самое (час 0, шаг 30 в 0–23)
    @Scheduled(cron = "0 0 0 * * *")
    public void eventNotificationNoteCleanTableScheduleProcess() {
        getInfo(eventNotificationNoteCleanTableScheduleProcessor.getSchedulerName());
        eventNotificationNoteCleanTableScheduleProcessor.process();
    }

    // каждые 10 минут проверяем, кому пора слать "доброе утро"
    @Scheduled(cron = "0 */10 * * * *")
    public void dailyNotificationSchedulerProcessor() {
        getInfo(dailyNotificationSchedulerProcessor.getSchedulerName());
        dailyNotificationSchedulerProcessor.process();
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void userAuthNotificationSchedulerProcessor() {
        getInfo(userAuthNotificationSchedulerProcessor.getSchedulerName());
        userAuthNotificationSchedulerProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
