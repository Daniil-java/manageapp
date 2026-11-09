package com.kuklin.manageapp.bots.caloriebot.onboarding;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsRepository;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirstDishNudgeSchedulerProcessorTest {

    private static final long USER_ID = 7L;
    private static final long CHAT_ID = 700L;
    // Пятница, 06.11.2026, по UTC (пояс пользователя — UTC)
    private static final Instant DAY1_10 = Instant.parse("2026-11-06T10:00:00Z");

    private final UserSettingsRepository repository = mock(UserSettingsRepository.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final CalorieTexts texts = mock(CalorieTexts.class);
    private final FirstDishNudgeSchedulerProcessor processor =
            new FirstDishNudgeSchedulerProcessor(repository, telegramUserService, bot, texts);

    @BeforeEach
    void setUp() {
        TelegramUser tgUser = mock(TelegramUser.class);
        when(tgUser.getTelegramId()).thenReturn(CHAT_ID);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(tgUser));
        when(texts.get(eq(USER_ID), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(repository.claimFirstDishNudge(eq(USER_ID), anyInt(), anyInt())).thenReturn(1);
    }

    private void candidate(Instant joined, int sent) {
        UserSettings settings = new UserSettings().setUserId(USER_ID).setTimezoneId("UTC")
                .setRemindersEnabled(true).setCreatedAt(joined).setFirstDishNudgeCount(sent);
        when(repository.findFirstDishNudgeCandidates(any(), anyInt(), anyString())).thenReturn(List.of(settings));
    }

    @Test
    void tooEarlyAfterStartSendsNothing() {
        candidate(DAY1_10, 0);

        processor.processAt(DAY1_10.plus(Duration.ofHours(2)));

        verifyNoInteractions(bot);
        verify(repository, never()).claimFirstDishNudge(anyLong(), anyInt(), anyInt());
    }

    @Test
    void firstNudgeThreeHoursAfterStart() {
        candidate(DAY1_10, 0);

        processor.processAt(DAY1_10.plus(Duration.ofHours(3)));

        verify(repository).claimFirstDishNudge(USER_ID, 0, 1);
        verify(bot).sendReturnedMessage(CHAT_ID, BotTextKeys.REMINDER_FIRST_DISH_FIRST);
    }

    @Test
    void secondNudgeNextDayAtLunch() {
        candidate(DAY1_10, 1);

        processor.processAt(Instant.parse("2026-11-07T13:00:00Z"));

        verify(repository).claimFirstDishNudge(USER_ID, 1, 2);
        verify(bot).sendReturnedMessage(CHAT_ID, BotTextKeys.REMINDER_FIRST_DISH_SECOND);
    }

    @Test
    void secondNudgeWaitsForLunch() {
        candidate(DAY1_10, 1);

        processor.processAt(Instant.parse("2026-11-07T10:00:00Z"));
        processor.processAt(DAY1_10.plus(Duration.ofHours(5)));

        verifyNoInteractions(bot);
    }

    @Test
    void quietHoursSendNothing() {
        candidate(Instant.parse("2026-11-06T19:00:00Z"), 0);

        processor.processAt(Instant.parse("2026-11-06T23:20:00Z"));

        verifyNoInteractions(bot);
    }

    @Test
    void lateEveningStartGetsFirstNudgeInTheMorning() {
        candidate(Instant.parse("2026-11-06T22:30:00Z"), 0);

        processor.processAt(Instant.parse("2026-11-07T09:00:00Z"));

        verify(bot).sendReturnedMessage(CHAT_ID, BotTextKeys.REMINDER_FIRST_DISH_FIRST);
    }

    @Test
    void missedFirstDayJumpsToSecondNudge() {
        candidate(DAY1_10, 0);

        processor.processAt(Instant.parse("2026-11-07T12:00:00Z"));

        verify(repository).claimFirstDishNudge(USER_ID, 0, 2);
        verify(bot).sendReturnedMessage(CHAT_ID, BotTextKeys.REMINDER_FIRST_DISH_SECOND);
    }

    @Test
    void alreadyClaimedSendsNothing() {
        candidate(DAY1_10, 0);
        when(repository.claimFirstDishNudge(USER_ID, 0, 1)).thenReturn(0);

        processor.processAt(DAY1_10.plus(Duration.ofHours(4)));

        verifyNoInteractions(bot);
    }
}
