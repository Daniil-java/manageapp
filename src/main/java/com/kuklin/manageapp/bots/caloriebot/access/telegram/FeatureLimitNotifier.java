package com.kuklin.manageapp.bots.caloriebot.access.telegram;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.PremiumOfferButtons;

import com.kuklin.manageapp.bots.caloriebot.access.AiRateLimiter;
import com.kuklin.manageapp.bots.caloriebot.access.PlanFeatureService;
import com.kuklin.manageapp.bots.caloriebot.access.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.web.error.ErrorParams;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.access.model.BotFeature;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static com.kuklin.manageapp.bots.caloriebot.access.CalorieAccessService.FREE_PLAN_CODE;

/**
 * Единая точка уведомления пользователя о том, что лимит функции исчерпан.
 * Используется всеми обработчиками бота, которые получают отказ от {@link com.kuklin.manageapp.bots.caloriebot.access.RequiresFeature}
 * или от {@link AiRateLimiter} (слишком частые обращения к ИИ).
 */
@Component
@RequiredArgsConstructor
public class FeatureLimitNotifier {
    private final CalorieTelegramBot calorieTelegramBot;
    private final PlanFeatureService planFeatureService;
    private final CalorieTexts texts;
    private final PremiumOfferButtons premiumOfferButtons;

    public void sendLimitReached(Long chatId, Long appUserId, BotFeature feature) {
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendReturnedMessage(chatId, buildMessage(feature, language),
                getSubscriptionKeyboard(language), null);
    }

    /**
     * Лимит обращений к ИИ (AiRateLimiter): за минуту — просим подождать,
     * за сутки без подписки — предлагаем подписку (у неё лимит выше).
     * Тексты — те же, что у ошибок API (errors_*: AI_RATE_LIMIT, AI_DAILY_LIMIT), склонения там уже есть.
     */
    public void sendAiRateLimited(Long chatId, Long appUserId, AiRateLimiter.Decision decision) {
        AppLanguage language = texts.languageOf(appUserId);
        if (decision.reason() == AiRateLimiter.Reason.MINUTE) {
            calorieTelegramBot.sendReturnedMessage(chatId, "⏳ " + texts.get(language,
                    ErrorStatus.AI_RATE_LIMIT.getTextKey(), ErrorParams.retryAfter(decision.retryAfterSeconds())));
            return;
        }

        String message = "🔒 " + texts.get(language, ErrorStatus.AI_DAILY_LIMIT.getTextKey(),
                ErrorParams.dailyLimit(decision.retryAfterSeconds(), decision.dailyLimit()));
        if (decision.premium()) {
            calorieTelegramBot.sendReturnedMessage(chatId, message);
        } else {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    message + "\n\n" + texts.get(language, BotTextKeys.LIMIT_AI_SUBSCRIPTION_HINT),
                    getSubscriptionKeyboard(language), null);
        }
    }

    private String buildMessage(BotFeature feature, AppLanguage language) {
        return texts.get(language, BotTextKeys.LIMIT_FEATURE,
                texts.get(language, feature.getTextKey()), describeFreeLimit(feature, language));
    }

    /**
     * Лимит берётся из plan_features, чтобы текст не расходился с реальными настройками тарифа.
     */
    private String describeFreeLimit(BotFeature feature, AppLanguage language) {
        PlanFeature freeFeature = planFeatureService.getFeaturesByPlanCode(FREE_PLAN_CODE, BotIdentifier.CALORIE_BOT)
                .stream()
                .filter(pf -> pf.getFeature() == feature)
                .findFirst()
                .orElse(null);

        if (freeFeature == null || freeFeature.getLimitValue() == null || freeFeature.getLimitValue() == 0) {
            return texts.get(language, BotTextKeys.LIMIT_FREE_ONLY_SUBSCRIPTION);
        }

        String limit = String.valueOf(freeFeature.getLimitValue());
        return switch (freeFeature.getLimitPeriod()) {
            case DAILY -> texts.get(language, BotTextKeys.LIMIT_FREE_DAILY, limit);
            case MONTHLY -> texts.get(language, BotTextKeys.LIMIT_FREE_MONTHLY, limit);
            case LIFETIME -> texts.get(language, BotTextKeys.LIMIT_FREE_LIFETIME, limit);
            case UNLIMITED -> texts.get(language, BotTextKeys.LIMIT_FREE_UNLIMITED);
        };
    }

    /** «💎 Premium — от 299 ⭐» и «🎁 Бесплатно — позвать друга». */
    private InlineKeyboardMarkup getSubscriptionKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(premiumOfferButtons.buildPremiumButton(language))
                .row(premiumOfferButtons.buildInviteButton(language))
                .build();
    }
}
