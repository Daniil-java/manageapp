package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.onboarding.CalculatorStartParam.Calculator;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile.ActivityLevel;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile.Goal;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile.Sex;
import com.kuklin.manageapp.bots.caloriebot.profile.telegram.CalculatorProfileUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.referral.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.referral.UtmService;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartUpdateHandlerTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long CHAT_ID = 100L;
    private static final Calculator CALCULATOR =
            new Calculator(Sex.FEMALE, 28, 168, 65, ActivityLevel.LOW, Goal.LOSE_WEIGHT);

    private final UserSubscriptionService subscriptionService = mock(UserSubscriptionService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final CalculatorProfileUpdateHandler calculatorHandler = mock(CalculatorProfileUpdateHandler.class);
    private final UserSettingsService settingsService = mock(UserSettingsService.class);
    private StartUpdateHandler handler;

    @BeforeEach
    void setUp() {
        // Язык не выбран: settingsService.findLanguageCode — null
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), settingsService);
        SampleDishCalorieUpdateHandler sampleDishHandler = mock(SampleDishCalorieUpdateHandler.class);
        when(sampleDishHandler.buildSampleButton(any())).thenReturn(new InlineKeyboardButton("sample"));
        handler = new StartUpdateHandler(subscriptionService, bot, mock(UtmService.class),
                mock(ReferralRewardService.class), calculatorHandler, mock(CalorieMenuButtons.class), texts,
                settingsService, sampleDishHandler);
    }

    @Test
    void firstStartSavesPendingWelcomeToDb() {
        Instant now = Instant.now();
        when(subscriptionService.createSubscriptionByFreePlanOrNull(APP_USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(new UserSubscription().setStartAt(now).setEndAt(now.plus(Duration.ofDays(3))));

        handler.handle(buildStartUpdate("/start " + CALCULATOR.encode(), "es"), buildTelegramUser());

        verify(settingsService).savePendingStart(APP_USER_ID, CALCULATOR.encode(), 3, null);
        verify(calculatorHandler, never()).offer(anyLong(), any(), any());
    }

    @Test
    void repeatedStartDoesNotOverwritePending() {
        handler.handle(buildStartUpdate("/start", "es"), buildTelegramUser());

        verify(settingsService).savePendingStart(APP_USER_ID, null, null, null);
    }

    @Test
    void unsupportedTelegramLanguageAsksLanguage() {
        handler.handle(buildStartUpdate("/start", "es"), buildTelegramUser());

        verify(settingsService, never()).setLanguageCode(anyLong(), any());
        verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), anyString(), any(InlineKeyboardMarkup.class), isNull());
    }

    @Test
    void russianTelegramLanguageSkipsQuestion() {
        handler.handle(buildStartUpdate("/start " + CALCULATOR.encode(), "ru"), buildTelegramUser());

        verify(settingsService).setLanguageCode(APP_USER_ID, "ru");
        verify(settingsService, never()).savePendingStart(anyLong(), any(), any(), any());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), any(), isNull());
        assertThat(text.getAllValues().get(0)).contains("Русский", "сменить: /lang");
        verify(calculatorHandler).offer(CHAT_ID, APP_USER_ID, CALCULATOR);
    }

    @Test
    void englishTelegramLanguageWelcomesInEnglish() {
        handler.handle(buildStartUpdate("/start", "en-GB"), buildTelegramUser());

        verify(settingsService).setLanguageCode(APP_USER_ID, "en");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), any(), isNull());
        assertThat(text.getAllValues().get(0)).contains("Hi!", "English", "change: /lang");
        assertThat(text.getAllValues().get(1)).contains("Log your first meal");
    }

    @Test
    void chosenLanguageWelcomeWithoutLanguageLine() {
        when(settingsService.findLanguageCode(APP_USER_ID)).thenReturn("ru");

        handler.handle(buildStartUpdate("/start", "en"), buildTelegramUser());

        verify(settingsService, never()).setLanguageCode(anyLong(), any());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), any(), isNull());
        assertThat(text.getAllValues().get(0)).contains("Привет").doesNotContain("/lang");
    }

    @Test
    void continueStartShowsPendingFromDb() {
        UserSettings settings = mock(UserSettings.class);
        when(settings.getPendingStartCalculator()).thenReturn(CALCULATOR.encode());
        when(settings.getPendingStartTrialDays()).thenReturn(3);
        when(settingsService.takePendingStart(APP_USER_ID)).thenReturn(Optional.of(settings));

        handler.continueStart(CHAT_ID, APP_USER_ID);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), any(), isNull());
        assertThat(text.getAllValues().get(0)).contains("3");
        verify(calculatorHandler).offer(CHAT_ID, APP_USER_ID, CALCULATOR);
    }

    @Test
    void welcomeEndsWithFirstDishCallAndButtons() {
        when(settingsService.takePendingStart(APP_USER_ID)).thenReturn(Optional.empty());

        handler.continueStart(CHAT_ID, APP_USER_ID);

        ArgumentCaptor<ReplyKeyboard> keyboards = ArgumentCaptor.forClass(ReplyKeyboard.class);
        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), anyString(), keyboards.capture(), isNull());
        InlineKeyboardMarkup callKeyboard = (InlineKeyboardMarkup) keyboards.getAllValues().get(1);
        assertThat(callKeyboard.getKeyboard()).hasSize(2);
        assertThat(callKeyboard.getKeyboard().get(1).get(0).getCallbackData())
                .isEqualTo(IntroCalorieUpdateHandler.NEW_MESSAGE_DATA);
    }

    @Test
    void continueStartWithoutPendingShowsPlainWelcome() {
        when(settingsService.takePendingStart(APP_USER_ID)).thenReturn(Optional.empty());

        handler.continueStart(CHAT_ID, APP_USER_ID);

        verify(bot, times(2)).sendReturnedMessage(eq(CHAT_ID.longValue()), anyString(), any(), isNull());
        verify(calculatorHandler, never()).offer(anyLong(), any(), any());
    }

    private static Update buildStartUpdate(String text, String telegramLanguageCode) {
        Chat chat = new Chat();
        chat.setId(CHAT_ID);
        User from = new User();
        from.setLanguageCode(telegramLanguageCode);
        Message message = new Message();
        message.setChat(chat);
        message.setFrom(from);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static TelegramUser buildTelegramUser() {
        return new TelegramUser().setAppUserId(APP_USER_ID);
    }
}
