package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.common.configurations.BotScheduler;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@BotScheduler(BotIdentifier.CALORIE_BOT)
public class CalorieSchedulerService {
    private final DailySummarySchedulerProcessor dailySummarySchedulerProcessor;
    private final MealReminderSchedulerProcessor mealReminderSchedulerProcessor;

    // Локально выключать (CALORIE_DAILY_SUMMARY_ENABLED=false): иначе отчёт за день с вызовом ИИ
    // уходит всем пользователям локальной базы
    @Value("${calorie.daily-summary.enabled:true}")
    private boolean dailySummaryEnabled;

    @Scheduled(cron = "0 */30 * * * *")
    public void dailySummarySchedulerProcessor() {
        if (!dailySummaryEnabled) return;
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
