package com.kuklin.manageapp.payment.services.scheduler.processors;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.components.texts.PaymentTextResolver;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubscriptionScheduleProcessorTest {
    private static final long APP_USER_ID = 7L;
    private static final long TELEGRAM_ID = 425L;

    private final UserSubscriptionService subscriptionService = mock(UserSubscriptionService.class);
    private final TelegramBotRegistry botRegistry = mock(TelegramBotRegistry.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final PaymentTextResolver textResolver = mock(PaymentTextResolver.class);
    private final TelegramBot bot = mock(TelegramBot.class);
    private final SubscriptionScheduleProcessor processor =
            new SubscriptionScheduleProcessor(subscriptionService, botRegistry, telegramUserService, textResolver);

    private final UserSubscription subscription = new UserSubscription()
            .setId(1L)
            .setAppUserId(APP_USER_ID)
            .setBotIdentifier(BotIdentifier.CALORIE_BOT);

    @BeforeEach
    void setUp() {
        when(botRegistry.get(BotIdentifier.CALORIE_BOT)).thenReturn(bot);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(APP_USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(new TelegramUser().setTelegramId(TELEGRAM_ID)));
        when(subscriptionService.findExpiredCandidates(anyInt())).thenReturn(new SliceImpl<>(List.of()));
        when(subscriptionService.findScheduledCandidates(anyInt())).thenReturn(new SliceImpl<>(List.of()));
        when(textResolver.getText(BotIdentifier.CALORIE_BOT, PaymentText.SUBSCRIPTION_EXPIRED, APP_USER_ID))
                .thenReturn("expired");
        when(textResolver.buildSubscriptionStartedText(subscription)).thenReturn("started");
    }

    @Test
    void expiredSubscriptionGoesThroughBotsExpiredMessage() {
        when(subscriptionService.findExpiredCandidates(anyInt()))
                .thenReturn(new SliceImpl<>(List.of(subscription), Pageable.ofSize(100), false));
        when(subscriptionService.expireOne(1L)).thenReturn(Optional.of(subscription));

        processor.process();

        verify(bot).sendSubExpiredMessage(TELEGRAM_ID, "expired");
        verify(bot, never()).sendReturnedMessage(anyLong(), anyString());
    }

    /** Раньше начало подписки тоже шло через sendSubExpiredMessage — калорийный бот дописывал «Premium закончился». */
    @Test
    void startedSubscriptionIsPlainMessageWithoutExpiredAd() {
        when(subscriptionService.findScheduledCandidates(anyInt()))
                .thenReturn(new SliceImpl<>(List.of(subscription), Pageable.ofSize(100), false));
        when(subscriptionService.activateOne(1L)).thenReturn(Optional.of(subscription));

        processor.process();

        verify(bot).sendReturnedMessage(TELEGRAM_ID, "started");
        verify(bot, never()).sendSubExpiredMessage(anyLong(), anyString());
    }
}
