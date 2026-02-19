package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class CalorieSchedulerService {
    private final DailySummarySchedulerProcessor dailySummarySchedulerProcessor;
    private final MealReminderSchedulerProcessor mealReminderSchedulerProcessor;

    @Scheduled(cron = "0 */30 * * * *")
    public void dailySummarySchedulerProcessor() {
        getInfo(dailySummarySchedulerProcessor.getSchedulerName());
        dailySummarySchedulerProcessor.process();
    }

    @Scheduled(cron = "0 */20 * * * *")
    public void mealReminderSchedulerProcessor() {
        getInfo(mealReminderSchedulerProcessor.getSchedulerName());
        mealReminderSchedulerProcessor.process();
    }


    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
