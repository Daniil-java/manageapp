package com.kuklin.manageapp.payment.services;

import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * pre_checkout пропускает только счёт, который ещё ждёт оплаты: ссылки Telegram многоразовые,
 * и без проверки статуса оплаченный или несостоявшийся счёт можно оплатить ещё раз.
 */
class PaymentServicePreCheckoutTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 3L;
    private static final int STARS = 150;
    private static final String PAYLOAD = "premium-3-1";

    private PaymentRepository paymentRepository;
    private PricingPlanService pricingPlanService;
    private PaymentService service;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = mock(PaymentRepository.class);
        pricingPlanService = mock(PricingPlanService.class);
        service = new PaymentService(paymentRepository, mock(GenerationBalanceOperationService.class),
                pricingPlanService, mock(UserSubscriptionService.class), mock(PaymentFailedLogService.class));
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(new PricingPlan()
                .setId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setPriceMinor(STARS));
    }

    @Test
    void createdPaymentPasses() {
        givenPayment(Payment.PaymentStatus.CREATED);

        assertThat(service.checkPreCheckoutQuery(query(), USER_ID)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Payment.PaymentStatus.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
    void alreadyFinishedPaymentIsRejected(Payment.PaymentStatus status) {
        givenPayment(status);

        assertThat(service.checkPreCheckoutQuery(query(), USER_ID)).isFalse();
    }

    // Счёт выставили до смены цен, тариф уже в архиве — по старой цене не продаём
    @ParameterizedTest
    @EnumSource(value = PricingPlan.PlanStatus.class, names = {"ARCHIVED", "DISABLED"})
    void invoiceForWithdrawnPlanIsRejected(PricingPlan.PlanStatus planStatus) throws Exception {
        givenPayment(Payment.PaymentStatus.CREATED);
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(new PricingPlan()
                .setId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setPriceMinor(STARS)
                .setPlanStatus(planStatus));

        assertThat(service.checkPreCheckoutQuery(query(), USER_ID)).isFalse();
    }

    @Test
    void invoiceForAvailablePlanPasses() throws Exception {
        givenPayment(Payment.PaymentStatus.CREATED);
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(new PricingPlan()
                .setId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setPriceMinor(STARS)
                .setPlanStatus(PricingPlan.PlanStatus.AVAILABLE));

        assertThat(service.checkPreCheckoutQuery(query(), USER_ID)).isTrue();
    }

    private void givenPayment(Payment.PaymentStatus status) {
        when(paymentRepository.findByTelegramInvoicePayload(PAYLOAD)).thenReturn(Optional.of(new Payment()
                .setId(1L)
                .setAppUserId(USER_ID)
                .setPricingPlanId(PLAN_ID)
                .setCurrency(Currency.XTR)
                .setStarsAmount(STARS)
                .setTelegramInvoicePayload(PAYLOAD)
                .setStatus(status)));
    }

    private static PreCheckoutQuery query() {
        PreCheckoutQuery query = new PreCheckoutQuery();
        query.setInvoicePayload(PAYLOAD);
        query.setCurrency(Currency.XTR.name());
        query.setTotalAmount(STARS);
        return query;
    }
}
