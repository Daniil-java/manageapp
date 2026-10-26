package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Звёзды вернул Telegram без нашего /refund: подписку по платежу отменяем один раз — повтор уведомления
 * (refunded_payment после /refund, сверка) очередь подписок больше не трогает.
 */
class PaymentServiceTelegramRefundTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 3L;
    private static final String CHARGE = "charge-1";

    private UserSubscriptionService userSubscriptionService;
    private PaymentService service;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        userSubscriptionService = mock(UserSubscriptionService.class);
        PricingPlanService pricingPlanService = mock(PricingPlanService.class);
        service = new PaymentService(paymentRepository, mock(GenerationBalanceOperationService.class),
                pricingPlanService, userSubscriptionService, mock(PaymentFailedLogService.class));

        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(new PricingPlan()
                .setId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setPayloadType(PricingPlan.PricingPlanType.SUBSCRIPTION)
                .setBotIdentifier(BotIdentifier.CALORIE_BOT));

        payment = new Payment()
                .setId(1L)
                .setAppUserId(USER_ID)
                .setPricingPlanId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setStarsAmount(299)
                .setProviderPaymentId(CHARGE)
                .setBotIdentifier(BotIdentifier.CALORIE_BOT)
                .setStatus(Payment.PaymentStatus.SUCCESS);
        when(paymentRepository.findByProviderPaymentId(CHARGE)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void refundByTelegramCancelsSubscriptionOnce() throws Exception {
        assertThat(service.cancelRefundedTelegramPayment(CHARGE, USER_ID)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
        assertThat(payment.getProviderStatus()).isEqualTo(Payment.ProviderStatus.REFUND_SUCCEEDED);

        assertThat(service.cancelRefundedTelegramPayment(CHARGE, USER_ID)).isFalse();
        assertThat(service.cancelRefundedTelegramPayment(CHARGE, null)).isFalse();
        verify(userSubscriptionService, times(1)).cancelByPayment(payment);
    }

    @Test
    void reconciliationCancelsWithoutUser() throws Exception {
        assertThat(service.cancelRefundedTelegramPayment(CHARGE, null)).isTrue();
        verify(userSubscriptionService).cancelByPayment(payment);
    }

    @Test
    void refundFromAnotherUserIsIgnored() throws Exception {
        assertThat(service.cancelRefundedTelegramPayment(CHARGE, 99L)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        verify(userSubscriptionService, never()).cancelByPayment(any());
    }

    @Test
    void unknownChargeIsIgnored() throws Exception {
        assertThat(service.cancelRefundedTelegramPayment("unknown", USER_ID)).isFalse();
        verify(userSubscriptionService, never()).cancelByPayment(any());
    }
}
