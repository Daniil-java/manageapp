package com.kuklin.manageapp.bots.caloriebot.subscription;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.referral.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.PremiumOfferButtons;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.SubscriptionTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PremiumEndingSchedulerProcessorTest {

    private static final Long APP_USER_ID = 1L;
    private static final Long TELEGRAM_ID = 100L;
    /** 13:00 по UTC — день. */
    private static final Instant DAY = Instant.parse("2026-11-09T13:00:00Z");

    private final UserSettingsService userSettingsService = mock(UserSettingsService.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final SubscriptionTexts subscriptionTexts = mock(SubscriptionTexts.class);
    private final PremiumOfferButtons premiumOfferButtons = mock(PremiumOfferButtons.class);
    private final ReferralRewardService referralRewardService = mock(ReferralRewardService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private final TelegramUser telegramUser = mock(TelegramUser.class);
    private PremiumEndingSchedulerProcessor processor;

    @BeforeEach
    void setUp() {
        CalorieTexts texts = new CalorieTexts(new I18nProperties(), userSettingsService);
        processor = new PremiumEndingSchedulerProcessor(userSettingsService, telegramUserService,
                subscriptionTexts, premiumOfferButtons, referralRewardService, new CalorieMenuButtons(texts), bot, texts);
        when(userSettingsService.getZoneId(APP_USER_ID)).thenReturn(ZoneId.of("UTC"));
        when(userSettingsService.findPremiumEndingUserIds(any(), any())).thenReturn(List.of(APP_USER_ID));
        when(telegramUserService.findByAppUserIdAndBotIdentifier(eq(APP_USER_ID), any())).thenReturn(Optional.of(telegramUser));
        when(telegramUser.getTelegramId()).thenReturn(TELEGRAM_ID);
        when(subscriptionTexts.formatDateTime(eq(APP_USER_ID), any())).thenReturn("10 ноября в 09:00");
        when(premiumOfferButtons.buildExtendButton(any())).thenReturn(TelegramKeyboard.button("extend", "x"));
        when(premiumOfferButtons.buildInviteButton(any())).thenReturn(TelegramKeyboard.button("invite", "y"));
        when(referralRewardService.getPlanDays(ReferralRewardService.INVITER_PLAN_CODE)).thenReturn(7);
        when(userSettingsService.claimPremiumEndingNotice(eq(APP_USER_ID), any())).thenReturn(true);
    }

    @Test
    void warnsDuringDayWhenPremiumEndsWithinWindow() {
        Instant end = DAY.plus(Duration.ofHours(20));
        givenPremiumEnd(end);

        processor.processAt(DAY);

        verify(userSettingsService).claimPremiumEndingNotice(APP_USER_ID, end);
        verify(bot).sendReturnedMessage(eq(TELEGRAM_ID.longValue()),
                and(contains("Premium закончится 10 ноября в 09:00"), contains("+7 дней")),
                any(InlineKeyboardMarkup.class), isNull());
    }

    @Test
    void silentAtNight() {
        givenPremiumEnd(DAY.plus(Duration.ofHours(20)));

        processor.processAt(Instant.parse("2026-11-09T23:30:00Z"));

        verifyNoInteractions(bot);
    }

    @Test
    void alreadyWarnedAboutThisEnd() {
        givenPremiumEnd(DAY.plus(Duration.ofHours(20)));
        when(userSettingsService.claimPremiumEndingNotice(eq(APP_USER_ID), any())).thenReturn(false);

        processor.processAt(DAY);

        verifyNoInteractions(bot);
    }

    @Test
    void extendedPastWindowNoWarning() {
        // Выборка была до продления: по подпискам конец уже дальше окна
        givenPremiumEnd(DAY.plus(Duration.ofDays(30)));

        processor.processAt(DAY);

        verify(userSettingsService, never()).claimPremiumEndingNotice(any(), any());
        verifyNoInteractions(bot);
    }

    @Test
    void blockedBotNoWarning() {
        givenPremiumEnd(DAY.plus(Duration.ofHours(20)));
        when(telegramUser.getIsBotBlocked()).thenReturn(true);

        processor.processAt(DAY);

        verifyNoInteractions(bot);
    }

    private void givenPremiumEnd(Instant end) {
        when(subscriptionTexts.findWorkingSubscriptions(APP_USER_ID))
                .thenReturn(List.of(new UserSubscription().setEndAt(end)));
    }
}
