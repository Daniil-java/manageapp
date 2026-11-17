package com.kuklin.manageapp.bots.caloriebot.onboarding;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.SampleDishCalorieUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.StartUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

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

    private final UserSettingsService settingsService = mock(UserSettingsService.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final CalorieTexts texts = mock(CalorieTexts.class);
    private final StartUpdateHandler startUpdateHandler = mock(StartUpdateHandler.class);
    private final SampleDishCalorieUpdateHandler sampleDishHandler = mock(SampleDishCalorieUpdateHandler.class);
    private final FirstDishNudgeSchedulerProcessor processor = new FirstDishNudgeSchedulerProcessor(
            settingsService, telegramUserService, bot, texts, startUpdateHandler, sampleDishHandler);

    @BeforeEach
    void setUp() {
        TelegramUser tgUser = mock(TelegramUser.class);
        when(tgUser.getTelegramId()).thenReturn(CHAT_ID);
        when(tgUser.getLanguageCode()).thenReturn("es");
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(tgUser));
        when(texts.get(eq(USER_ID), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(settingsService.claimFirstDishNudge(eq(USER_ID), anyInt(), anyInt())).thenReturn(true);
        when(sampleDishHandler.buildSampleButton(any())).thenReturn(new InlineKeyboardButton("sample"));
    }

    private UserSettings candidate(Instant joined, int sent) {
        UserSettings settings = new UserSettings().setUserId(USER_ID).setTimezoneId("UTC").setLanguage("ru")
                .setRemindersEnabled(true).setCreatedAt(joined).setFirstDishNudgeCount(sent);
        when(settingsService.findFirstDishNudgeCandidates(any(), anyInt())).thenReturn(List.of(settings));
        return settings;
    }

    private void verifySent(String key) {
        verify(bot).sendReturnedMessage(eq(CHAT_ID), eq(key), any(), isNull());
    }

    @Test
    void tooEarlyAfterStartSendsNothing() {
        candidate(DAY1_10, 0);

        processor.processAt(DAY1_10.plus(Duration.ofHours(2)));

        verifyNoInteractions(bot);
        verify(settingsService, never()).claimFirstDishNudge(anyLong(), anyInt(), anyInt());
    }

    @Test
    void firstNudgeThreeHoursAfterStartWithSampleButton() {
        candidate(DAY1_10, 0);

        processor.processAt(DAY1_10.plus(Duration.ofHours(3)));

        verify(settingsService).claimFirstDishNudge(USER_ID, 0, 1);
        verify(bot).sendReturnedMessage(eq(CHAT_ID), eq(BotTextKeys.REMINDER_FIRST_DISH_FIRST), notNull(), isNull());
    }

    @Test
    void sampleAlreadySeenNudgeWithoutButtons() {
        candidate(DAY1_10, 0).setFirstDishSampleAt(DAY1_10.plus(Duration.ofMinutes(1)));

        processor.processAt(DAY1_10.plus(Duration.ofHours(3)));

        verify(bot).sendReturnedMessage(CHAT_ID, BotTextKeys.REMINDER_FIRST_DISH_FIRST, null, null);
    }

    @Test
    void languageNotChosenAsksLanguageAgain() {
        candidate(DAY1_10, 0).setLanguage(null);

        processor.processAt(DAY1_10.plus(Duration.ofHours(3)));

        verify(settingsService).claimFirstDishNudge(USER_ID, 0, 1);
        verify(startUpdateHandler).sendLanguageQuestion(CHAT_ID, "es");
        verifyNoInteractions(bot);
    }

    @Test
    void secondNudgeNextDayAtLunch() {
        candidate(DAY1_10, 1);

        processor.processAt(Instant.parse("2026-11-07T13:00:00Z"));

        verify(settingsService).claimFirstDishNudge(USER_ID, 1, 2);
        verifySent(BotTextKeys.REMINDER_FIRST_DISH_SECOND);
    }

    @Test
    void eveningStartGetsSecondNudgeNextDay() {
        candidate(Instant.parse("2026-11-06T17:40:00Z"), 1);

        processor.processAt(Instant.parse("2026-11-07T12:00:00Z"));

        verify(settingsService).claimFirstDishNudge(USER_ID, 1, 2);
        verifySent(BotTextKeys.REMINDER_FIRST_DISH_SECOND);
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

        verifySent(BotTextKeys.REMINDER_FIRST_DISH_FIRST);
    }

    @Test
    void missedFirstDayJumpsToSecondNudge() {
        candidate(DAY1_10, 0);

        processor.processAt(Instant.parse("2026-11-07T12:00:00Z"));

        verify(settingsService).claimFirstDishNudge(USER_ID, 0, 2);
        verifySent(BotTextKeys.REMINDER_FIRST_DISH_SECOND);
    }

    @Test
    void alreadyClaimedSendsNothing() {
        candidate(DAY1_10, 0);
        when(settingsService.claimFirstDishNudge(USER_ID, 0, 1)).thenReturn(false);

        processor.processAt(DAY1_10.plus(Duration.ofHours(4)));

        verifyNoInteractions(bot);
    }
}
