package com.kuklin.manageapp.bots.caloriebot.scheduler;

import com.kuklin.manageapp.bots.caloriebot.onboarding.FirstDishNudgeSchedulerProcessor;
import com.kuklin.manageapp.bots.caloriebot.settings.MealReminderSchedulerProcessor;
import com.kuklin.manageapp.bots.caloriebot.report.DailySummarySchedulerProcessor;
import com.kuklin.manageapp.bots.caloriebot.subscription.PremiumEndingSchedulerProcessor;
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
    private final FirstDishNudgeSchedulerProcessor firstDishNudgeSchedulerProcessor;
    private final PremiumEndingSchedulerProcessor premiumEndingSchedulerProcessor;
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

    @Scheduled(cron = "0 */20 * * * *")
    public void firstDishNudgeSchedulerProcessor() {
        getInfo(firstDishNudgeSchedulerProcessor.getSchedulerName());
        firstDishNudgeSchedulerProcessor.process();
    }

    // «Premium скоро закончится» — раз в полчаса хватает: окно предупреждения — 30 часов
    @Scheduled(cron = "0 10/30 * * * *")
    public void premiumEndingSchedulerProcessor() {
        getInfo(premiumEndingSchedulerProcessor.getSchedulerName());
        premiumEndingSchedulerProcessor.process();
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
