package com.kuklin.manageapp.bots.caloriebot.onboarding;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.SampleDishCalorieUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.StartUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.scheduler.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.time.Instant;

/**
 * Напоминания новичку, который так и не записал ни одного блюда. Обычное напоминание о еде
 * (MealReminderSchedulerProcessor) таким не уходит: оно считает от последнего блюда.
 * <ol>
 *     <li>через {@link #FIRST_AFTER} после прихода;</li>
 *     <li>на следующий день, в обед ({@link #LUNCH_FROM}–{@link #LUNCH_TO} по времени пользователя), но не раньше
 *     {@link #SECOND_AFTER} после прихода — пришедший до ~20:00 получит его завтра, позже — послезавтра.</li>
 * </ol>
 * Больше двух не шлём, ночью ({@link #QUIET_FROM}–{@link #QUIET_TO}) не шлём, пришедшим раньше
 * {@link #CANDIDATE_WINDOW} не шлём — старые пользователи без блюд их не получат.
 * Первое блюдо останавливает напоминания: такие пользователи не попадают в выборку.
 * Кто ещё не смотрел пример записи — под напоминанием кнопка «Посмотреть пример». Кто так и не выбрал язык —
 * вместо напоминания снова вопрос о языке: ответ продолжит отложенный /start.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstDishNudgeSchedulerProcessor implements ScheduleProcessor {
    static final Duration FIRST_AFTER = Duration.ofHours(3);
    static final Duration SECOND_AFTER = Duration.ofHours(16);
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
    private final StartUpdateHandler startUpdateHandler;
    private final SampleDishCalorieUpdateHandler sampleDishHandler;

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
        telegramUserService.findByAppUserIdAndBotIdentifier(settings.getUserId(), BotIdentifier.CALORIE_BOT)
                .ifPresent(tgUser -> sendNudge(settings, tgUser, second));
    }

    private void sendNudge(UserSettings settings, TelegramUser tgUser, boolean second) {
        if (settings.getLanguage() == null) {
            startUpdateHandler.sendLanguageQuestion(tgUser.getTelegramId(), tgUser.getLanguageCode());
            return;
        }
        Long userId = settings.getUserId();
        String key = second ? BotTextKeys.REMINDER_FIRST_DISH_SECOND : BotTextKeys.REMINDER_FIRST_DISH_FIRST;
        calorieTelegramBot.sendReturnedMessage(tgUser.getTelegramId(), texts.get(userId, key),
                buildKeyboardOrNull(settings), null);
    }

    /** «Посмотреть пример» — кто ещё не смотрел; посмотревшим — без кнопок. */
    private InlineKeyboardMarkup buildKeyboardOrNull(UserSettings settings) {
        if (settings.getFirstDishSampleAt() != null) return null;
        return TelegramKeyboard.builder()
                .row(sampleDishHandler.buildSampleButton(texts.languageOf(settings.getUserId())))
                .build();
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
