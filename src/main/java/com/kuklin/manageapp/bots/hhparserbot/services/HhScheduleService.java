package com.kuklin.manageapp.bots.hhparserbot.services;

import com.kuklin.manageapp.bots.hhparserbot.processors.HhNotificationScheduleProcessor;
import com.kuklin.manageapp.bots.hhparserbot.processors.HhOpenAiScheduleProcessor;
import com.kuklin.manageapp.bots.hhparserbot.processors.HhVacancyScheduleProcessor;
import com.kuklin.manageapp.bots.hhparserbot.processors.HhWorkFilterScheduleProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class HhScheduleService {
    private final HhWorkFilterScheduleProcessor hhWorkFilterScheduleProcessor;
    private final HhOpenAiScheduleProcessor hhOpenAiScheduleProcessor;
    private final HhVacancyScheduleProcessor hhVacancyScheduleProcessor;
    private final HhNotificationScheduleProcessor hhNotificationScheduleProcessor;

    @Scheduled(cron = "0 0 */4 * * *")   // каждые 4 часа: 00:00, 04:00, 08:00...
    public void workFilterScheduleProcess() {
        getInfo(hhWorkFilterScheduleProcessor.getSchedulerName());
        hhWorkFilterScheduleProcessor.process();
    }

    @Scheduled(cron = "0 15 */4 * * *")  // каждые 4 часа + сдвиг 15 мин
    public void openAiScheduleProcess() {
        getInfo(hhOpenAiScheduleProcessor.getSchedulerName());
        hhOpenAiScheduleProcessor.process();
    }

    @Scheduled(cron = "0 30 */4 * * *")  // каждые 4 часа + сдвиг 30 мин
    public void vacancyScheduleProcess() {
        getInfo(hhVacancyScheduleProcessor.getSchedulerName());
        hhVacancyScheduleProcessor.process();
    }

    @Scheduled(cron = "0 45 */4 * * *")  // каждые 4 часа + сдвиг 45 мин
    public void notificationScheduleProcess() {
        getInfo(hhNotificationScheduleProcessor.getSchedulerName());
        hhNotificationScheduleProcessor.process();
    }

    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
