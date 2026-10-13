package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PaymentResponse;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.CreateInvoiceLinkWithTelegramSubscription;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.services.UserAuthIdentityService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PaymentService;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CaloriePaymentServiceTest {

    private static final long USER_ID = 7L;
    private static final long PLAN_ID = 3L;

    private PaymentService paymentService;
    private PricingPlanService pricingPlanService;
    private CalorieTelegramBot bot;
    private TelegramUserService telegramUserService;
    private UserAuthIdentityService identityService;
    private CaloriePaymentService service;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        pricingPlanService = mock(PricingPlanService.class);
        bot = mock(CalorieTelegramBot.class);
        telegramUserService = mock(TelegramUserService.class);
        identityService = mock(UserAuthIdentityService.class);
        service = new CaloriePaymentService(mock(CommonPaymentFacade.class), paymentService, pricingPlanService,
                bot, mock(UserSubscriptionService.class), telegramUserService, identityService);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.empty());
    }

    private static PricingPlan plan(int days, int stars) {
        return new PricingPlan()
                .setId(PLAN_ID)
                .setTitle("Premium " + days)
                .setDescription("desc")
                .setDurationDays(days)
                .setPriceMinor(stars)
                .setCurrency(Currency.XTR)
                .setPayloadType(PricingPlan.PricingPlanType.SUBSCRIPTION)
                .setPlanStatus(PricingPlan.PlanStatus.AVAILABLE)
                .setBotIdentifier(BotIdentifier.CALORIE_BOT);
    }

    @Test
    void accountWithoutTelegramCannotPay() {
        when(identityService.hasTelegram(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createPaymentLink(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.PAYMENT_TELEGRAM_REQUIRED));
        verifyNoInteractions(paymentService);
    }

    @Test
    void oldBotUserWithoutIdentityCanPay() throws Exception {
        when(identityService.hasTelegram(USER_ID)).thenReturn(false);
        when(telegramUserService.findByAppUserIdAndBotIdentifier(USER_ID, BotIdentifier.CALORIE_BOT))
                .thenReturn(Optional.of(new TelegramUser()));
        PricingPlan plan = plan(30, 150);
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(plan);
        when(paymentService.createNewPayment(BotIdentifier.CALORIE_BOT, USER_ID, plan, Payment.Provider.STARS))
                .thenReturn(payment(150));
        when(bot.execute(any(CreateInvoiceLink.class))).thenReturn("https://t.me/$abc");

        assertThat(service.createPaymentLink(USER_ID, PLAN_ID).getPaymentUrl()).isEqualTo("https://t.me/$abc");
    }

    @Test
    void planOfAnotherBotOrDisabledIsRejected() throws Exception {
        when(identityService.hasTelegram(USER_ID)).thenReturn(true);
        when(pricingPlanService.getPricingPlanById(PLAN_ID))
                .thenReturn(plan(30, 150).setBotIdentifier(BotIdentifier.ASSISTANT_BOT));

        assertThatThrownBy(() -> service.createPaymentLink(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.PRICING_PLAN_NOT_AVAILABLE));

        when(pricingPlanService.getPricingPlanById(PLAN_ID))
                .thenReturn(plan(30, 150).setPlanStatus(PricingPlan.PlanStatus.DISABLED));

        assertThatThrownBy(() -> service.createPaymentLink(USER_ID, PLAN_ID))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        e -> assertThat(e.getErrorStatus()).isEqualTo(ErrorStatus.PRICING_PLAN_NOT_AVAILABLE));
        verifyNoInteractions(paymentService);
    }

    @Test
    void ninetyDayPlanGetsOneTimeInvoiceNotTelegramSubscription() throws Exception {
        when(identityService.hasTelegram(USER_ID)).thenReturn(true);
        PricingPlan plan = plan(90, 350);
        when(pricingPlanService.getPricingPlanById(PLAN_ID)).thenReturn(plan);
        when(paymentService.createNewPayment(BotIdentifier.CALORIE_BOT, USER_ID, plan, Payment.Provider.STARS))
                .thenReturn(payment(350));
        when(bot.execute(any(CreateInvoiceLink.class))).thenReturn("https://t.me/$abc");

        PaymentResponse response = service.createPaymentLink(USER_ID, PLAN_ID);

        ArgumentCaptor<CreateInvoiceLink> captor = ArgumentCaptor.forClass(CreateInvoiceLink.class);
        verify(bot).execute(captor.capture());
        CreateInvoiceLink invoice = captor.getValue();
        assertThat(invoice).isNotInstanceOf(CreateInvoiceLinkWithTelegramSubscription.class);
        assertThat(invoice.getPayload()).isEqualTo("payload-1");
        assertThat(invoice.getCurrency()).isEqualTo("XTR");
        assertThat(invoice.getPrices()).singleElement()
                .satisfies(price -> assertThat(price.getAmount()).isEqualTo(350));
        assertThat(response.getPaymentId()).isEqualTo("1");
    }

    private static Payment payment(int stars) {
        return new Payment()
                .setId(1L)
                .setTelegramInvoicePayload("payload-1")
                .setStarsAmount(stars)
                .setStatus(Payment.PaymentStatus.CREATED);
    }
}
