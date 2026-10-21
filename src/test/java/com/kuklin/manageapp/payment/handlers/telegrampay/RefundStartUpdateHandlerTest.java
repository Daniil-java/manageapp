package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.models.StarsRefundClient;
import com.kuklin.manageapp.payment.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundStartUpdateHandlerTest {

    private static final long ADMIN_TG = 425120436L;
    private static final long PAYER_TG = 555L;
    private static final long PAYER_APP_USER = 77L;
    private static final String CHARGE = "charge-1";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final TelegramBotRegistry registry = mock(TelegramBotRegistry.class);
    private final TelegramUserService telegramUserService = mock(TelegramUserService.class);
    private final TelegramBot paymentBot = mock(TelegramBot.class);
    private final TelegramBot calorieBot = mock(TelegramBot.class);
    private final StarsRefundClient refundClient = mock(StarsRefundClient.class);
    private RefundStartUpdateHandler handler;

    @BeforeEach
    void setUp() {
        handler = spy(new RefundStartUpdateHandler(paymentService, registry, telegramUserService));
        ReflectionTestUtils.setField(handler, "adminTelegramIds", Set.of(ADMIN_TG));
        when(registry.get(BotIdentifier.PAYMENT)).thenReturn(paymentBot);
        when(registry.get(BotIdentifier.CALORIE_BOT)).thenReturn(calorieBot);
        when(calorieBot.getToken()).thenReturn("calorie-token");
        doReturn(refundClient).when(handler).buildStarsRefundClient("calorie-token");

        Payment payment = new Payment().setId(10L).setAppUserId(PAYER_APP_USER)
                .setBotIdentifier(BotIdentifier.CALORIE_BOT).setStarsAmount(150);
        when(paymentService.getRefundableTelegramPaymentOrNull(CHARGE)).thenReturn(payment);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(PAYER_APP_USER, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(new TelegramUser().setTelegramId(PAYER_TG)));
    }

    private static Update buildRefundUpdate(String text) {
        Message message = new Message();
        message.setText(text);
        Chat chat = new Chat();
        chat.setId(ADMIN_TG);
        message.setChat(chat);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static TelegramUser buildUser(long telegramId) {
        return new TelegramUser().setTelegramId(telegramId).setBotIdentifier(BotIdentifier.PAYMENT);
    }

    @Test
    void nonAdminIsIgnored() throws Exception {
        handler.handle(buildRefundUpdate("/refund " + CHARGE), buildUser(PAYER_TG));

        verifyNoInteractions(paymentService);
        verify(refundClient, never()).refund(anyLong(), anyString());
        verify(paymentBot, never()).sendReturnedMessage(anyLong(), anyString());
    }

    @Test
    void adminRefundsWithPaymentBotTokenToPayerThenMarksPayment() throws Exception {
        when(refundClient.refund(PAYER_TG, CHARGE)).thenReturn(true);
        when(paymentService.refundTelegramPayment(CHARGE)).thenReturn(true);

        handler.handle(buildRefundUpdate("/refund " + CHARGE), buildUser(ADMIN_TG));

        verify(refundClient).refund(PAYER_TG, CHARGE);
        verify(paymentService).refundTelegramPayment(CHARGE);
        verify(paymentBot).sendReturnedMessage(eq(ADMIN_TG), contains("✅"));
    }

    @Test
    void failedTelegramRefundLeavesDatabaseUntouched() throws Exception {
        when(refundClient.refund(PAYER_TG, CHARGE)).thenReturn(false);

        handler.handle(buildRefundUpdate("/refund " + CHARGE), buildUser(ADMIN_TG));

        verify(paymentService, never()).refundTelegramPayment(anyString());
        verify(paymentBot).sendReturnedMessage(eq(ADMIN_TG), contains("в БД ничего не менял"));
    }

    @Test
    void unknownPaymentIsReported() throws Exception {
        when(paymentService.getRefundableTelegramPaymentOrNull("nope")).thenReturn(null);

        handler.handle(buildRefundUpdate("/refund nope"), buildUser(ADMIN_TG));

        verify(refundClient, never()).refund(anyLong(), anyString());
        verify(paymentBot).sendReturnedMessage(eq(ADMIN_TG), contains("не найден"));
    }

    @Test
    void missingChargeIdShowsUsage() {
        handler.handle(buildRefundUpdate("/refund"), buildUser(ADMIN_TG));

        verifyNoInteractions(paymentService);
        verify(paymentBot).sendReturnedMessage(eq(ADMIN_TG), contains("Формат"));
    }
}
