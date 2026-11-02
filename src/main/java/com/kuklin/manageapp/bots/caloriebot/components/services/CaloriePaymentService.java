package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.SubscriptionStatusDto;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PaymentResponse;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.PricingPlanDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.texts.PaymentModuleTexts;
import com.kuklin.manageapp.bots.caloriebot.texts.PlanTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.services.UserAuthIdentityService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.PaymentService;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import com.kuklin.manageapp.payment.services.UserSubscriptionService;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;

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
    private final UserAuthIdentityService userAuthIdentityService;
    private final PlanTexts planTexts;
    private final PaymentModuleTexts paymentModuleTexts;
    private final CalorieTexts texts;
    // Для звёзд (XTR) Telegram не проверяет providerToken, но пустым его передать нельзя
    private static final String TOKEN_DUMMY = "xtr_dummy";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());

    public List<PricingPlanDto> getAvailablePlans(AppLanguage language) {
        return commonPaymentFacade.getPricingPlans(BotIdentifier.CALORIE_BOT).stream()
                .map(plan -> PricingPlanDto.builder()
                        .id(plan.getId())
                        .codeForOrderId(plan.getCodeForOrderId())
                        .title(planTexts.getTitle(plan, language))
                        .description(planTexts.getDescription(plan, language))
                        .currency(plan.getCurrency())
                        .priceMinor(plan.getPriceMinor())
                        .payloadType(plan.getPayloadType())
                        .durationDays(plan.getDurationDays())
                        .build())
                .toList();
    }

    public PaymentResponse createPaymentLink(Long appUserId, Long planId, AppLanguage language) {
        // Платёж подтверждает бот по Telegram-аккаунту плательщика (pre_checkout_query) —
        // аккаунту без Telegram (вход по email) оплатить нечем, счёт не выставляем
        if (!hasTelegram(appUserId)) {
            throw new ErrorResponseException(ErrorStatus.PAYMENT_TELEGRAM_REQUIRED);
        }

        PricingPlan plan = getPurchasablePlan(planId);

        // Сначала запись в базе, потом Telegram: в счёт уходит payload с id платежа. Не одной транзакцией —
        // она держала бы соединение с БД всё время HTTP-запроса к Telegram.
        Payment payment = null;
        try {
            // 1. Создаем системную запись о платеже
            payment = paymentService.createNewPayment(
                    BotIdentifier.CALORIE_BOT,
                    appUserId,
                    plan,
                    Payment.Provider.STARS
            );

            // 2. Разовый счёт, как в боте (SendInvoiceBuilder). Не Telegram-подписка:
            // у неё период всегда 30 дней, а тарифы бывают и на 90.
            CreateInvoiceLink invoice = TelegramBot.buildOneTimeInvoiceLink(
                    planTexts.getTitle(plan, language),
                    planTexts.getInvoiceDescription(plan, language),
                    payment.getTelegramInvoicePayload(),
                    TOKEN_DUMMY,
                    payment.getStarsAmount(),
                    plan.getCurrency(),
                    paymentModuleTexts.getText(PaymentText.INVOICE_PRICE_LABEL, language)
            );

            // 3. Запрашиваем уникальную ссылку у Telegram
            String invoiceLink = calorieTelegramBot.execute(invoice);

            // 4. Возвращаем URL на фронтенд (WebApp.openInvoice)
            return PaymentResponse.builder()
                    .paymentId(payment.getId().toString())
                    .paymentUrl(invoiceLink)
                    .status(payment.getStatus().name())
                    .build();

        } catch (Exception e) {
            log.error("Ошибка при генерации ссылки на оплату звездами для пользователя {}", appUserId, e);
            markFailedQuietly(payment, e);
            throw new ErrorResponseException(ErrorStatus.PAYMENT_FAILED);
        }
    }

    // Ссылку пользователь не получил — платёж не должен висеть в CREATED. Ошибка пометки не должна
    // подменить исходную: пользователю всё равно уходит PAYMENT_FAILED, а причина — в логе выше.
    private void markFailedQuietly(Payment payment, Exception cause) {
        if (payment == null) return;   // упали ещё при создании записи — помечать нечего
        try {
            paymentService.markFailed(payment, "Invoice link not created: " + cause);
        } catch (Exception e) {
            log.error("Failed to mark payment {} as FAILED", payment.getId(), e);
        }
    }

    private boolean hasTelegram(Long appUserId) {
        // Старые пользователи бота могут быть без Telegram-identity — их узнаём по профилю в боте
        return userAuthIdentityService.hasTelegram(appUserId)
                || telegramUserService.findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT).isPresent();
    }

    // Только то, что показываем в /plans: тариф этого бота, доступный для покупки, в звёздах
    private PricingPlan getPurchasablePlan(Long planId) {
        PricingPlan plan;
        try {
            plan = pricingPlanService.getPricingPlanById(planId);
        } catch (PricingPlanNotFoundException e) {
            throw new ErrorResponseException(ErrorStatus.PRICING_PLAN_NOT_AVAILABLE);
        }
        if (plan.getBotIdentifier() != BotIdentifier.CALORIE_BOT
                || plan.getPlanStatus() != PricingPlan.PlanStatus.AVAILABLE
                || plan.getCurrency() != Currency.XTR) {
            throw new ErrorResponseException(ErrorStatus.PRICING_PLAN_NOT_AVAILABLE);
        }
        return plan;
    }

    @Transactional
    public List<SubscriptionStatusDto> getSubscriptionStatus(Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        return userSubscriptionService
                .getActiveAndScheduledSubscriptions(appUserId, BotIdentifier.CALORIE_BOT)
                .stream()
                .map(subscription -> new SubscriptionStatusDto()
                        .setStatus(subscription.getStatus())
                        .setExpiryDate(DATE_FORMATTER.format(subscription.getEndAt()))
                        // Подписи UserSubscription.Status в модуле оплаты русские — берём из словаря по имени
                        .setStatusMessage(texts.get(language,
                                BotTextKeys.buildSubscriptionStatusKey(subscription.getStatus()))))
                .toList(); // Если Java 16+, иначе .collect(Collectors.toList())
    }
}