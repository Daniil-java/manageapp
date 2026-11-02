package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * successful_payment: каждое списание — своя запись Payment. Два списания по одной ссылке
 * (оплата с двух устройств) не сливаются в одну запись, повтор того же списания ничего не выдаёт.
 */
class PaymentServiceSuccessfulPaymentTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 3L;
    private static final int STARS = 150;
    private static final String PAYLOAD = "premium-3-1";
    private static final String CHARGE_A = "charge-a";
    private static final String CHARGE_B = "charge-b";

    private PaymentRepository paymentRepository;
    private UserSubscriptionService userSubscriptionService;
    private PaymentService service;
    private Payment invoice;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = mock(PaymentRepository.class);
        userSubscriptionService = mock(UserSubscriptionService.class);
        PricingPlanService pricingPlanService = mock(PricingPlanService.class);
        service = new PaymentService(paymentRepository, mock(GenerationBalanceOperationService.class),
                pricingPlanService, userSubscriptionService, mock(PaymentFailedLogService.class));

        // 7 дней — раньше повтор по такому тарифу выдавался в ту же запись (особая ветка была только для 30 дней)
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(new PricingPlan()
                .setId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setPriceMinor(STARS)
                .setPayloadType(PricingPlan.PricingPlanType.SUBSCRIPTION)
                .setDurationDays(7)
                .setCodeForOrderId("premium")
                .setBotIdentifier(BotIdentifier.CALORIE_BOT));

        invoice = new Payment()
                .setId(1L)
                .setAppUserId(USER_ID)
                .setPricingPlanId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setStarsAmount(STARS)
                .setAmount(0)
                .setProvider(Payment.Provider.STARS)
                .setBotIdentifier(BotIdentifier.CALORIE_BOT)
                .setTelegramInvoicePayload(PAYLOAD)
                .setStatus(Payment.PaymentStatus.CREATED);
        when(paymentRepository.findByTelegramInvoicePayloadForUpdate(PAYLOAD)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByProviderPaymentId(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(2L);
            return saved;
        });
    }

    @Test
    void firstChargeMarksInvoicePaid() throws Exception {
        Payment result = service.processTelegramSuccessfulPaymentAndGetOrNull(payment(CHARGE_A), USER_ID);

        assertThat(result).isSameAs(invoice);
        assertThat(invoice.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        assertThat(invoice.getProviderPaymentId()).isEqualTo(CHARGE_A);
        verify(userSubscriptionService).createSubscriptionByPayment(invoice);
    }

    @Test
    void secondChargeOfSameInvoiceGetsOwnPayment() throws Exception {
        service.processTelegramSuccessfulPaymentAndGetOrNull(payment(CHARGE_A), USER_ID);

        Payment second = service.processTelegramSuccessfulPaymentAndGetOrNull(payment(CHARGE_B), USER_ID);

        assertThat(second).isNotSameAs(invoice);
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(second.getProviderPaymentId()).isEqualTo(CHARGE_B);
        assertThat(second.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        assertThat(second.getTelegramInvoicePayload()).isNotEqualTo(PAYLOAD);
        assertThat(second.getAppUserId()).isEqualTo(USER_ID);
        // первое списание не затёрто — его можно вернуть по CHARGE_A
        assertThat(invoice.getProviderPaymentId()).isEqualTo(CHARGE_A);

        ArgumentCaptor<Payment> granted = ArgumentCaptor.forClass(Payment.class);
        verify(userSubscriptionService, times(2)).createSubscriptionByPayment(granted.capture());
        assertThat(granted.getAllValues()).containsExactly(invoice, second);
    }

    @Test
    void repeatedChargeIsSkipped() throws Exception {
        Payment processed = new Payment().setId(1L).setProviderPaymentId(CHARGE_A);
        when(paymentRepository.findByProviderPaymentId(CHARGE_A)).thenReturn(Optional.of(processed));

        Payment result = service.processTelegramSuccessfulPaymentAndGetOrNull(payment(CHARGE_A), USER_ID);

        assertThat(result).isSameAs(processed);
        verify(userSubscriptionService, never()).createSubscriptionByPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    private static SuccessfulPayment payment(String chargeId) {
        SuccessfulPayment payment = new SuccessfulPayment();
        payment.setInvoicePayload(PAYLOAD);
        payment.setCurrency(Currency.XTR.name());
        payment.setTotalAmount(STARS);
        payment.setTelegramPaymentChargeId(chargeId);
        payment.setProviderPaymentChargeId("provider-" + chargeId);
        return payment;
    }
}
