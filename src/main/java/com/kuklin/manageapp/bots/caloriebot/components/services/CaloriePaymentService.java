package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.models.SubscriptionStatusDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PaymentResponse;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.CreateInvoiceLinkWithTelegramSubscription;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PaymentService;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaloriePaymentService {

    private final CommonPaymentFacade commonPaymentFacade;
    private final PaymentService paymentService;
    private final PricingPlanService pricingPlanService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSubscriptionService userSubscriptionService;
    private final TelegramUserService telegramUserService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    public List<PricingPlan> getAvailablePlans() {
        return commonPaymentFacade.getPricingPlans(BotIdentifier.CALORIE_BOT);
    }

    public PaymentResponse createPaymentLink(Long appUserId, Long planId) {
        try {
            // 1. Получаем выбранный тариф
            PricingPlan plan = pricingPlanService.getPricingPlanById(planId);

            // 2. Создаем системную запись о платеже
            Payment payment = paymentService.createNewPayment(
                    BotIdentifier.CALORIE_BOT,
                    appUserId,
                    plan,
                    Payment.Provider.STARS
            );

            int starsAmount = plan.getCurrency().equals(Currency.XTR) ? plan.getPriceMinor() : 0;

            // 3. Формируем запрос к Telegram API
            CreateInvoiceLinkWithTelegramSubscription subscriptionLink =
                    TelegramBot.buildCreateInvoiceLink(
                            plan.getTitle(),
                            plan.getDescription(),
                            payment.getTelegramInvoicePayload(),
                            plan.getPriceMinor(),
                            plan.getCurrency()
                    );

            // 4. Запрашиваем уникальную ссылку у Telegram
            String invoiceLink = calorieTelegramBot.execute(subscriptionLink);

            // 5. Возвращаем URL на фронтенд
            return PaymentResponse.builder()
                    .paymentId(payment.getId().toString())
                    .paymentUrl(invoiceLink)
                    .status(payment.getStatus().name())
                    .build();

        } catch (Exception e) {
            log.error("Ошибка при генерации ссылки на оплату звездами для пользователя {}", appUserId, e);
            throw new ErrorResponseException(ErrorStatus.PAYMENT_FAILED);
        }
    }

    @Transactional
    public List<SubscriptionStatusDto> getSubscriptionStatus(Long appUserId) {
        return userSubscriptionService
                .getActiveAndScheduledSubscriptions(appUserId, BotIdentifier.CALORIE_BOT)
                .stream()
                .map(subscription -> new SubscriptionStatusDto()
                        .setStatus(subscription.getStatus())
                        .setExpiryDate(DATE_FORMATTER.format(subscription.getEndAt()))
                        .setStatusMessage(subscription.getStatus().getCommandText()))
                .toList(); // Если Java 16+, иначе .collect(Collectors.toList())
    }
}