package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.dish.DishAddedEvent;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InvitePromptSender;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsRepository;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.services.TelegramUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirstDishFollowUpSenderTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long CHAT_ID = 100L;

    private final UserSettingsRepository repository = mock(UserSettingsRepository.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final UserNutritionProfileService profileService = mock(UserNutritionProfileService.class);
    private final InvitePromptSender invitePromptSender = mock(InvitePromptSender.class);
    private final UserNutritionProfile profile = mock(UserNutritionProfile.class);
    private FirstDishFollowUpSender sender;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
        sender = new FirstDishFollowUpSender(repository, telegramUserService, bot, profileService, invitePromptSender, texts);
        when(profileService.getOrCreateProfile(APP_USER_ID)).thenReturn(profile);
    }

    @Test
    void withoutProfileInviteThenChannelAndProfileButtonLast() {
        when(profile.checkTargetCalculateParams()).thenReturn(false);

        sender.send(CHAT_ID, APP_USER_ID);

        InOrder order = inOrder(invitePromptSender, bot);
        order.verify(invitePromptSender).sendStartFlowInvite(CHAT_ID, APP_USER_ID);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        order.verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture(), any(ReplyKeyboard.class), isNull());
        assertThat(text.getValue()).contains("Первое блюдо", "@zephyr_ai", "Заполни профиль");
    }

    @Test
    void withProfileNoProfileButton() {
        when(profile.checkTargetCalculateParams()).thenReturn(true);

        sender.send(CHAT_ID, APP_USER_ID);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), text.capture());
        assertThat(text.getValue()).contains("Первое блюдо", "@zephyr_ai").doesNotContain("Заполни профиль");
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString(), any(ReplyKeyboard.class), any());
    }

    @Test
    void notFirstDishSendsNothing() {
        when(repository.claimFirstDishFollowUp(eq(APP_USER_ID), any())).thenReturn(0);

        sender.onDishAdded(new DishAddedEvent(APP_USER_ID));

        verifyNoInteractions(telegramUserService, bot, invitePromptSender);
    }
}
