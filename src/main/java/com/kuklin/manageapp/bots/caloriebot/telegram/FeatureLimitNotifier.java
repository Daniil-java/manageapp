package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.AiRateLimiter;
import com.kuklin.manageapp.bots.caloriebot.components.services.PlanFeatureService;
import com.kuklin.manageapp.bots.caloriebot.entities.PlanFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService.FREE_PLAN_CODE;

/**
 * Единая точка уведомления пользователя о том, что лимит функции исчерпан.
 * Используется всеми обработчиками бота, которые получают отказ от {@link com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature}
 * или от {@link AiRateLimiter} (слишком частые обращения к ИИ).
 */
@Component
@RequiredArgsConstructor
public class FeatureLimitNotifier {
    private final CalorieTelegramBot calorieTelegramBot;
    private final PlanFeatureService planFeatureService;

    public void sendLimitReached(Long chatId, BotFeature feature) {
        calorieTelegramBot.sendReturnedMessage(chatId, buildMessage(feature), getSubscriptionKeyboard(), null);
    }

    /**
     * Лимит обращений к ИИ (AiRateLimiter): за минуту — просим подождать,
     * за сутки без подписки — предлагаем подписку (у неё лимит выше).
     */
    public void sendAiRateLimited(Long chatId, AiRateLimiter.Decision decision) {
        if (decision.reason() == AiRateLimiter.Reason.MINUTE) {
            calorieTelegramBot.sendReturnedMessage(chatId, String.format(
                    "⏳ Слишком много запросов подряд. Подождите %d сек. и попробуйте снова.",
                    decision.retryAfterSeconds()));
            return;
        }

        String message = String.format(
                "🔒 Достигнут лимит запросов к ИИ: %d за сутки. Следующий запрос будет доступен через %s.",
                decision.dailyLimit(), formatWait(decision.retryAfterSeconds()));
        if (decision.premium()) {
            calorieTelegramBot.sendReturnedMessage(chatId, message);
        } else {
            calorieTelegramBot.sendReturnedMessage(chatId, message + "\n\nС подпиской лимит выше.",
                    getSubscriptionKeyboard(), null);
        }
    }

    /** 125 сек → "3 мин", 7300 сек → "2 ч 2 мин". */
    private static String formatWait(long seconds) {
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " мин";
        return minutes / 60 + " ч " + minutes % 60 + " мин";
    }

    private String buildMessage(BotFeature feature) {
        return "🔒 «" + feature.getDisplayName() + "»: " + describeFreeLimit(feature) + "\n\n"
                + "Оформите подписку, чтобы пользоваться без ограничений.";
    }

    /**
     * Лимит берётся из plan_features, чтобы текст не расходился с реальными настройками тарифа.
     */
    private String describeFreeLimit(BotFeature feature) {
        PlanFeature freeFeature = planFeatureService.getFeaturesByPlanCode(FREE_PLAN_CODE, BotIdentifier.CALORIE_BOT)
                .stream()
                .filter(pf -> pf.getFeature() == feature)
                .findFirst()
                .orElse(null);

        if (freeFeature == null || freeFeature.getLimitValue() == null || freeFeature.getLimitValue() == 0) {
            return "доступно только по подписке.";
        }

        int limit = freeFeature.getLimitValue();
        return switch (freeFeature.getLimitPeriod()) {
            case DAILY -> "на бесплатном плане доступно " + limit + " в день. Лимит обновится завтра.";
            case MONTHLY -> "на бесплатном плане доступно " + limit + " в месяц. Лимит обновится в следующем месяце.";
            case LIFETIME -> "на бесплатном плане доступно не больше " + limit + ".";
            case UNLIMITED -> "лимит бесплатного плана достигнут.";
        };
    }

    private InlineKeyboardMarkup getSubscriptionKeyboard() {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText(),
                        Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText()))
                .build();
    }
}
