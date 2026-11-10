package com.kuklin.manageapp.bots.caloriebot.onboarding;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.scheduler.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Напоминания новичку, который так и не записал ни одного блюда. Обычное напоминание о еде
 * (MealReminderSchedulerProcessor) таким не уходит: оно считает от последнего блюда.
 * <ol>
 *     <li>через {@link #FIRST_AFTER} после прихода;</li>
 *     <li>на следующий день, в обед ({@link #LUNCH_FROM}–{@link #LUNCH_TO} по времени пользователя).</li>
 * </ol>
 * Больше двух не шлём, ночью ({@link #QUIET_FROM}–{@link #QUIET_TO}) не шлём, пришедшим раньше
 * {@link #CANDIDATE_WINDOW} не шлём — старые пользователи без блюд их не получат.
 * Первое блюдо останавливает напоминания: такие пользователи не попадают в выборку.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstDishNudgeSchedulerProcessor implements ScheduleProcessor {
    static final Duration FIRST_AFTER = Duration.ofHours(3);
    static final Duration SECOND_AFTER = Duration.ofHours(22);
    static final Duration CANDIDATE_WINDOW = Duration.ofDays(3);
    static final int MAX_NUDGES = 2;
    static final int LUNCH_FROM = 12;
    static final int LUNCH_TO = 15;
    static final int QUIET_FROM = 23;
    static final int QUIET_TO = 9;

    private final UserSettingsService userSettingsService;
    private final TelegramUserService telegramUserService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    @Override
    public void process() {
        processAt(Instant.now());
    }

    void processAt(Instant now) {
        for (UserSettings settings : userSettingsService.findFirstDishNudgeCandidates(
                now.minus(CANDIDATE_WINDOW), MAX_NUDGES)) {
            try {
                processUser(settings, now);
            } catch (Exception e) {
                log.error("[FirstDishNudge] Error for userId={}", settings.getUserId(), e);
            }
        }
    }

    private void processUser(UserSettings settings, Instant now) {
        int hour = now.atZone(settings.getZoneId()).getHour();
        if (hour >= QUIET_FROM || hour < QUIET_TO) {
            return;
        }
        Duration age = Duration.between(settings.getCreatedAt(), now);
        int sent = settings.getFirstDishNudgeCount();
        // Первое проспали (пришёл поздно вечером, ночь) и уже завтра — сразу второе, первое не догоняем
        boolean second = sent > 0 || age.compareTo(SECOND_AFTER) >= 0;
        if (second) {
            if (age.compareTo(SECOND_AFTER) < 0 || hour < LUNCH_FROM || hour >= LUNCH_TO) {
                return;
            }
        } else if (age.compareTo(FIRST_AFTER) < 0) {
            return;
        }

        int newCount = second ? MAX_NUDGES : 1;
        if (!userSettingsService.claimFirstDishNudge(settings.getUserId(), sent, newCount)) {
            return;
        }
        String key = second ? BotTextKeys.REMINDER_FIRST_DISH_SECOND : BotTextKeys.REMINDER_FIRST_DISH_FIRST;
        telegramUserService.findByAppUserIdAndBotIdentifier(settings.getUserId(), BotIdentifier.CALORIE_BOT)
                .ifPresent(tgUser -> calorieTelegramBot.sendReturnedMessage(
                        tgUser.getTelegramId(), texts.get(settings.getUserId(), key)));
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
