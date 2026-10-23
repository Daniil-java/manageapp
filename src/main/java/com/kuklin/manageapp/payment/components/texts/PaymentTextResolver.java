package com.kuklin.manageapp.payment.components.texts;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Тексты модуля оплаты для пользователя бота: переводы бота (PaymentBotTexts), если они есть,
 * иначе тексты по умолчанию — PaymentText, title / description тарифа из БД, подписи enum.
 */
@Component
@RequiredArgsConstructor
public class PaymentTextResolver {
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final List<PaymentBotTexts> botTexts;

    public String getText(BotIdentifier bot, PaymentText text, Long appUserId) {
        return findBotTexts(bot)
                .map(texts -> texts.getText(text, appUserId))
                .orElse(text.getDefaultText());
    }

    public String getPlanTitle(PricingPlan plan, Long appUserId) {
        return findBotTexts(plan.getBotIdentifier())
                .map(texts -> texts.getPlanTitle(plan, appUserId))
                .orElse(plan.getTitle());
    }

    public String getPlanInvoiceDescription(PricingPlan plan, Long appUserId) {
        return findBotTexts(plan.getBotIdentifier())
                .map(texts -> texts.getPlanInvoiceDescription(plan, appUserId))
                .orElse(plan.getDescription());
    }

    public String getProviderTitle(BotIdentifier bot, Payment.Provider provider, Long appUserId) {
        return findBotTexts(bot)
                .map(texts -> texts.getProviderTitle(provider, appUserId))
                .orElse(provider.getTitle());
    }

    public String buildSubscriptionStartedText(UserSubscription subscription) {
        return findBotTexts(subscription.getBotIdentifier())
                .map(texts -> texts.buildSubscriptionStartedText(subscription))
                .orElseGet(() -> buildDefaultSubscriptionStartedText(subscription));
    }

    // Период — в UTC: у модуля оплаты нет часового пояса пользователя
    private static String buildDefaultSubscriptionStartedText(UserSubscription sub) {
        ZoneId utc = ZoneId.of("UTC");
        String period = (sub.getStartAt() != null && sub.getEndAt() != null)
                ? String.format("%s — %s",
                sub.getStartAt().atZone(utc).format(PERIOD_FORMAT), sub.getEndAt().atZone(utc).format(PERIOD_FORMAT))
                : "не задан";
        return String.format("Действие подписки началось! \n📌 Статус: %s\n⏳ Период: %s",
                sub.getStatus().getCommandText(), period);
    }

    private Optional<PaymentBotTexts> findBotTexts(BotIdentifier bot) {
        return botTexts.stream()
                .filter(texts -> texts.getBotIdentifier() == bot)
                .findFirst();
    }
}
