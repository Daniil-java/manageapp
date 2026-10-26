package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService.ReferralReward;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService.RewardType;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.models.events.DishAddedEvent;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.models.SubscriptionPurchasedEvent;
import com.kuklin.manageapp.payment.models.SubscriptionRefundedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferralRewardNotifierTest {

    private static final long INVITED = 100L;
    private static final long REFERRER = 7L;
    private static final long INVITED_CHAT = 1100L;
    private static final long REFERRER_CHAT = 1007L;

    private final ReferralRewardService rewardService = mock(ReferralRewardService.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final CalorieTelegramBot bot = mock(CalorieTelegramBot.class);
    private ReferralRewardNotifier notifier;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(INVITED)).thenReturn("ru");
        when(settings.findLanguageCode(REFERRER)).thenReturn("en");
        notifier = new ReferralRewardNotifier(rewardService, telegramUserService, bot,
                new CalorieTexts(new I18nProperties(), settings));

        givenChat(INVITED, INVITED_CHAT, false);
        givenChat(REFERRER, REFERRER_CHAT, false);
        when(rewardService.getActivationDays()).thenReturn(3);
        when(rewardService.getInviterLimitDays()).thenReturn(14);
    }

    private void givenChat(long appUserId, long telegramId, boolean blocked) {
        when(telegramUserService.findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(new TelegramUser().setAppUserId(appUserId).setTelegramId(telegramId)
                        .setIsBotBlocked(blocked)));
    }

    private void givenDishRewards(ReferralReward... rewards) {
        when(rewardService.rewardDishAdded(INVITED)).thenReturn(List.of(rewards));
    }

    @Test
    void invitedIsNotifiedInRussian() {
        givenDishRewards(new ReferralReward(RewardType.INVITED_FIRST_DISH, INVITED, 3, false));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot).sendReturnedMessage(eq(INVITED_CHAT), contains("+3 дня Premium"));
        verify(bot, never()).sendReturnedMessage(eq(REFERRER_CHAT), anyString());
    }

    @Test
    void referrerIsNotifiedAboutActiveFriendInEnglish() {
        givenDishRewards(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 7, false));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot).sendReturnedMessage(eq(REFERRER_CHAT), contains("kept a diary for 3 days"));
        verify(bot).sendReturnedMessage(eq(REFERRER_CHAT), contains("+7 days of Premium"));
    }

    @Test
    void referrerAtLimitIsToldAboutLimit() {
        givenDishRewards(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 0, true));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot).sendReturnedMessage(eq(REFERRER_CHAT), contains("capped at 14 days"));
    }

    @Test
    void zeroDaysWithoutLimitIsSilent() {
        // Тарифа нет в БД — дней не выдано, а лимит не набран: сообщать нечего
        givenDishRewards(new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 0, false));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
    }

    @Test
    void friendPaymentNotifiesReferrer() {
        when(rewardService.rewardFriendPaymentOrNull(INVITED, 1L))
                .thenReturn(new ReferralReward(RewardType.INVITER_FRIEND_PAID, REFERRER, 30, false));

        notifier.onSubscriptionPurchased(new SubscriptionPurchasedEvent(INVITED, BotIdentifier.CALORIE_BOT, 1L));

        verify(bot).sendReturnedMessage(eq(REFERRER_CHAT), contains("+30 days of Premium"));
    }

    @Test
    void refundNotifiesReferrerAboutRevokedBonus() {
        when(rewardService.revokeFriendPaymentRewardOrNull(INVITED, 1L))
                .thenReturn(new ReferralReward(RewardType.INVITER_FRIEND_PAID_REVOKED, REFERRER, 30, false));

        notifier.onSubscriptionRefunded(new SubscriptionRefundedEvent(INVITED, BotIdentifier.CALORIE_BOT, 1L));

        verify(bot).sendReturnedMessage(eq(REFERRER_CHAT), contains("(30 days) has been cancelled"));
    }

    @Test
    void otherBotPurchaseIsIgnored() {
        notifier.onSubscriptionPurchased(new SubscriptionPurchasedEvent(INVITED, BotIdentifier.ASSISTANT_BOT, 1L));

        verifyNoInteractions(rewardService);
    }

    @Test
    void rewardFailureDoesNotPropagate() {
        when(rewardService.rewardDishAdded(INVITED)).thenThrow(new IllegalStateException("db down"));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
    }

    @Test
    void blockedRecipientIsSkipped() {
        givenChat(REFERRER, REFERRER_CHAT, true);
        givenDishRewards(new ReferralReward(RewardType.INVITED_FIRST_DISH, INVITED, 3, false),
                new ReferralReward(RewardType.INVITER_FRIEND_ACTIVE, REFERRER, 7, false));

        notifier.onDishAdded(new DishAddedEvent(INVITED));

        verify(bot).sendReturnedMessage(eq(INVITED_CHAT), anyString());
        verify(bot, never()).sendReturnedMessage(eq(REFERRER_CHAT), anyString());
    }
}
