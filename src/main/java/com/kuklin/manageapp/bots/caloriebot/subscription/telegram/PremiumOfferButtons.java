package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.landing.CalorieLandingService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.handlers.PaymentProviderChoiceUpdateHandler;
import com.kuklin.manageapp.payment.services.PricingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Comparator;
import java.util.List;

/**
 * Кнопки предложения Premium — одни и те же в тарифах, «Premium закончился», «скоро закончится» и лимитах:
 * тарифы с ценой и выгодой на кнопке, «💎 Premium — от 299 ⭐» (открывает тарифы) и «🎁 Бесплатно — позвать
 * друга» (экран приглашений).
 */
@Component
@RequiredArgsConstructor
public class PremiumOfferButtons {
    private final PricingPlanService pricingPlanService;
    private final PaymentProviderChoiceUpdateHandler providerChoiceHandler;
    private final CalorieTexts texts;

    /** Тарифы в продаже, от короткого к длинному. */
    public List<PricingPlan> findPlansForSale() {
        return pricingPlanService.getAllPlansByBotIdentifierAndPlanStatusAvailable(BotIdentifier.CALORIE_BOT).stream()
                .filter(plan -> plan.getDurationDays() != null && plan.getPriceMinor() != null)
                .sorted(Comparator.comparing(PricingPlan::getDurationDays))
                .toList();
    }

    /** «1 месяц — 299 ⭐», «3 месяца — 699 ⭐ · −22%»: выгода — к самому короткому тарифу в той же валюте. */
    public List<InlineKeyboardButton> buildPlanButtons(AppLanguage language) {
        List<PricingPlan> plans = findPlansForSale();
        return plans.stream()
                .map(plan -> TelegramKeyboard.button(buildPlanLabel(plan, findShortest(plans, plan), language),
                        providerChoiceHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + plan.getId(),
                        ButtonStyle.SUCCESS))
                .toList();
    }

    String buildPlanLabel(PricingPlan plan, PricingPlan shortest, AppLanguage language) {
        String duration = describeDuration(plan.getDurationDays(), language);
        String price = CalorieLandingService.formatPrice(plan.getPriceMinor(), plan.getCurrency(), language);
        Integer saving = CalorieLandingService.savingPercent(plan, shortest);
        return saving == null
                ? texts.get(language, BotTextKeys.PAYMENT_PLAN_BUTTON, duration, price)
                : texts.get(language, BotTextKeys.PAYMENT_PLAN_BUTTON_SAVING, duration, price, saving);
    }

    /** «💎 Premium — от 299 ⭐» → тарифы; без тарифов в продаже — «💎 Тарифы». */
    public InlineKeyboardButton buildPremiumButton(AppLanguage language) {
        return buildPlansButton(language, BotTextKeys.PAYMENT_BUTTON_PREMIUM_FROM);
    }

    /** «💎 Продлить — от 299 ⭐» → тарифы: Premium ещё действует. */
    public InlineKeyboardButton buildExtendButton(AppLanguage language) {
        return buildPlansButton(language, BotTextKeys.PAYMENT_BUTTON_EXTEND_FROM);
    }

    private InlineKeyboardButton buildPlansButton(AppLanguage language, String labelKey) {
        List<PricingPlan> plans = findPlansForSale();
        String label = plans.isEmpty()
                ? texts.get(language, BotTextKeys.PAYMENT_BUTTON_PLANS)
                : texts.get(language, labelKey, findCheapestPrice(plans, language));
        return TelegramKeyboard.button(label, Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText(), ButtonStyle.PRIMARY);
    }

    /** «🎁 Бесплатно — позвать друга» → экран приглашений. */
    public InlineKeyboardButton buildInviteButton(AppLanguage language) {
        return TelegramKeyboard.button(texts.get(language, BotTextKeys.PAYMENT_BUTTON_FREE_INVITE),
                Command.CALORIE_UTM.getCommandText());
    }

    private String findCheapestPrice(List<PricingPlan> plans, AppLanguage language) {
        // Самый короткий тариф — самый дешёвый; цены разных валют не сравниваем
        PricingPlan cheapest = plans.get(0);
        return CalorieLandingService.formatPrice(cheapest.getPriceMinor(), cheapest.getCurrency(), language);
    }

    private static PricingPlan findShortest(List<PricingPlan> sortedPlans, PricingPlan plan) {
        return sortedPlans.stream().filter(p -> p.getCurrency() == plan.getCurrency()).findFirst().orElse(plan);
    }

    /** «1 месяц», «3 месяца», «12 месяцев», «45 дней». */
    private String describeDuration(int days, AppLanguage language) {
        if (days % 365 == 0) {
            return texts.get(language, BotTextKeys.PAYMENT_DURATION_MONTHS, days / 365 * 12);
        }
        return days % 30 == 0
                ? texts.get(language, BotTextKeys.PAYMENT_DURATION_MONTHS, days / 30)
                : texts.get(language, BotTextKeys.PAYMENT_DURATION_DAYS, days);
    }
}
