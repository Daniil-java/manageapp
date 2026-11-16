package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Тарифы и «Premium закончился» (PaymentPlanListCalorieUpdateHandler). */
class PaymentPlanListCalorieUpdateHandlerTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long CHAT_ID = 100L;

    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final SubscriptionTexts subscriptionTexts = mock(SubscriptionTexts.class);
    private final PremiumOfferButtons premiumOfferButtons = mock(PremiumOfferButtons.class);
    private PaymentPlanListCalorieUpdateHandler handler;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), mock(UserSettingsService.class));
        handler = new PaymentPlanListCalorieUpdateHandler(bot, texts, new CalorieMenuButtons(texts),
                telegramUserService, subscriptionTexts, premiumOfferButtons);
        TelegramUser telegramUser = mock(TelegramUser.class);
        when(telegramUser.getAppUserId()).thenReturn(APP_USER_ID);
        when(telegramUserService.getTelegramUserByTelegramIdAndBotIdentifierOrNull(eq(CHAT_ID), any())).thenReturn(telegramUser);
        when(premiumOfferButtons.buildPremiumButton(any())).thenReturn(TelegramKeyboard.button("premium", "x"));
        when(premiumOfferButtons.buildInviteButton(any())).thenReturn(TelegramKeyboard.button("invite", "y"));
    }

    @Test
    void lastSubscriptionEndedSendsOffer() {
        when(subscriptionTexts.findWorkingSubscriptions(APP_USER_ID)).thenReturn(List.of());

        handler.sendSubExpiredAdMessage(CHAT_ID);

        verify(bot).sendReturnedMessage(eq(CHAT_ID.longValue()), contains("Premium закончился"),
                any(InlineKeyboardMarkup.class), isNull());
    }

    @Test
    void nextSubscriptionInChainSilent() {
        when(subscriptionTexts.findWorkingSubscriptions(APP_USER_ID)).thenReturn(List.of(new UserSubscription()));

        handler.sendSubExpiredAdMessage(CHAT_ID);

        verifyNoInteractions(bot);
    }

    @Test
    void plansCaptionGoesAsHtml() {
        when(subscriptionTexts.buildStatusLine(APP_USER_ID)).thenReturn("💎 <b>Premium до 28 ноября</b>");
        when(premiumOfferButtons.buildPlanButtons(any())).thenReturn(List.of(TelegramKeyboard.button("1 месяц — 299 ⭐", "p")));
        TelegramUser user = mock(TelegramUser.class);
        when(user.getAppUserId()).thenReturn(APP_USER_ID);
        Message message = new Message();
        message.setChat(new Chat(CHAT_ID, "private"));
        Update update = new Update();
        update.setMessage(message);

        handler.handle(update, user);

        // Подпись с <b> — только с разметкой HTML, иначе теги приходят как текст
        verify(bot).sendHtmlPhotoMessage(eq(CHAT_ID.longValue()), any(), anyString(),
                contains("<b>Premium</b>"), any(InlineKeyboardMarkup.class));
        verify(bot, never()).sendPhotoMessage(anyLong(), any(byte[].class), anyString(), anyString(), any());
    }
}
