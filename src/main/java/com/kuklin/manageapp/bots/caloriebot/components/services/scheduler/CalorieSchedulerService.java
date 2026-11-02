package com.kuklin.manageapp.bots.caloriebot.components.services.scheduler;

import com.kuklin.manageapp.bots.caloriebot.support.SupportGroupService;
import com.kuklin.manageapp.common.scheduler.BotScheduler;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
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
    private final SupportGroupService supportGroupService;

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

    // Заявки без ответа дольше calorie.support.remind-after — напоминание в теме группы поддержки
    @Scheduled(cron = "0 */30 * * * *")
    public void supportReminderScheduler() {
        getInfo("SupportReminderScheduler");
        supportGroupService.remindUnanswered();
    }


    private void getInfo(String name) {
        log.info(name + " started working");
    }
}
