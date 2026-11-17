package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InviteLinks;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InvitePromptSender;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.SubscriptionTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntroCalorieUpdateHandlerTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long CHAT_ID = 100L;
    private static final Integer MESSAGE_ID = 7;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
    private final SubscriptionTexts subscriptionTexts = mock(SubscriptionTexts.class);
    private final UserNutritionProfile profile = mock(UserNutritionProfile.class);
    private final TelegramUser telegramUser = mock(TelegramUser.class);
    private IntroCalorieUpdateHandler handler;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
        handler = new IntroCalorieUpdateHandler(bot, profileService, mock(InvitePromptSender.class),
                mock(InviteLinks.class), subscriptionTexts,
                new CalorieMenuButtons(texts), texts);
        when(profileService.getOrCreateProfile(APP_USER_ID)).thenReturn(profile);
        when(telegramUser.getAppUserId()).thenReturn(APP_USER_ID);
    }

    @Test
    void commandSendsMenuWithAllSectionsAndEmptyProfileMark() {
        when(profile.checkTargetCalculateParams()).thenReturn(false);

        handler.handle(buildCommandUpdate(), telegramUser);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), keyboard.capture(), isNull());
        assertThat(text.getValue()).contains("Знакомство с Zefir");
        assertThat(findCallbacks(keyboard.getValue())).contains(
                "/intro food", "/intro profile", "/intro reminders", "/intro reports",
                "/intro premium", "/intro rewards", "/intro channel");
        assertThat(findLabels(keyboard.getValue())).anyMatch(label -> label.contains("Профиль") && label.contains("⚠️"));
    }

    @Test
    void sectionOpensInPlaceWithBackToMenu() {
        handler.handle(buildCallbackUpdate("/intro food"), telegramUser);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendEditMessage(eq(CHAT_ID.longValue()), text.capture(), eq(MESSAGE_ID.intValue()), keyboard.capture());
        assertThat(text.getValue()).contains("Как записывать еду", "голосовое");
        assertThat(findCallbacks(keyboard.getValue())).contains(IntroCalorieUpdateHandler.HOME_DATA);
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void backShowsMenuInPlace() {
        when(profile.checkTargetCalculateParams()).thenReturn(true);

        handler.handle(buildCallbackUpdate(IntroCalorieUpdateHandler.HOME_DATA), telegramUser);

        ArgumentCaptor<InlineKeyboardMarkup> keyboard = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(bot).sendEditMessage(eq(CHAT_ID.longValue()), contains("Знакомство с Zefir"), eq(MESSAGE_ID.intValue()),
                keyboard.capture());
        assertThat(findLabels(keyboard.getValue())).noneMatch(label -> label.contains("⚠️"));
    }

    @Test
    void premiumShowsStatusAndWhatIsIncluded() {
        when(subscriptionTexts.buildStatusLine(APP_USER_ID)).thenReturn("STATUS");

        handler.handle(buildCallbackUpdate("/intro premium"), telegramUser);

        verify(bot).sendEditMessage(eq(CHAT_ID.longValue()), and(startsWith("STATUS"), contains("Premium")), eq(MESSAGE_ID.intValue()), any());
    }

    private Update buildCommandUpdate() {
        Update update = new Update();
        update.setMessage(buildMessage());
        return update;
    }

    private Update buildCallbackUpdate(String data) {
        CallbackQuery callback = new CallbackQuery();
        callback.setData(data);
        callback.setMessage(buildMessage());
        Update update = new Update();
        update.setCallbackQuery(callback);
        return update;
    }

    private Message buildMessage() {
        Chat chat = new Chat(CHAT_ID, "private");
        Message message = new Message();
        message.setChat(chat);
        message.setMessageId(MESSAGE_ID);
        return message;
    }

    private List<String> findCallbacks(InlineKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream().flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData).filter(Objects::nonNull).toList();
    }

    private List<String> findLabels(InlineKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream().flatMap(List::stream).map(InlineKeyboardButton::getText).toList();
    }
}
