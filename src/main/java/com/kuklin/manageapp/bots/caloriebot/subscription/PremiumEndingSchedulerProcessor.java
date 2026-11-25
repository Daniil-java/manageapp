package com.kuklin.manageapp.bots.caloriebot.subscription;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.referral.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.PremiumOfferButtons;
import com.kuklin.manageapp.bots.caloriebot.subscription.telegram.SubscriptionTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.scheduler.ScheduleProcessor;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.time.Instant;

/**
 * «Premium закончится 13 ноября в 14:00» — заранее, пока можно продлить без перерыва: кнопки «Продлить» и
 * «Бесплатно — позвать друга». Раньше человек узнавал о конце Premium, только когда он уже закончился.
 * <p>
 * Конец Premium — конец последней подписки в цепочке (пробный → бонусы → купленные). Предупреждаем, когда до
 * него не больше {@link #NOTICE_WINDOW}, и только днём ({@link #SEND_FROM}–{@link #SEND_TO} по времени
 * пользователя): окна хватает, чтобы при любом времени конца нашёлся дневной час. Про один и тот же конец —
 * один раз (user_settings.premium_ending_notified_end_at); продлил и снова подходит к концу — предупредим снова.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumEndingSchedulerProcessor implements ScheduleProcessor {
    static final Duration NOTICE_WINDOW = Duration.ofHours(30);
    static final int SEND_FROM = 10;
    static final int SEND_TO = 21;

    private final UserSettingsService userSettingsService;
    private final TelegramUserService telegramUserService;
    private final SubscriptionTexts subscriptionTexts;
    private final PremiumOfferButtons premiumOfferButtons;
    private final ReferralRewardService referralRewardService;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    @Override
    public void process() {
        processAt(Instant.now());
    }

    void processAt(Instant now) {
        for (Long appUserId : userSettingsService.findPremiumEndingUserIds(
                now, now.plus(NOTICE_WINDOW))) {
            try {
                processUser(appUserId, now);
            } catch (Exception e) {
                log.error("[PremiumEnding] Error for userId={}", appUserId, e);
            }
        }
    }

    private void processUser(Long appUserId, Instant now) {
        int hour = now.atZone(userSettingsService.getZoneId(appUserId)).getHour();
        if (hour < SEND_FROM || hour >= SEND_TO) {
            return;
        }
        TelegramUser tgUser = telegramUserService
                .findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT).orElse(null);
        if (tgUser == null || Boolean.TRUE.equals(tgUser.getIsBotBlocked())) {
            return;
        }
        // Конец — по самим подпискам: сервис заодно синхронизирует статусы
        Instant end = SubscriptionTexts.findPremiumEndOrNull(subscriptionTexts.findWorkingSubscriptions(appUserId));
        if (end == null || !end.isAfter(now) || end.isAfter(now.plus(NOTICE_WINDOW))) {
            return;
        }
        if (!userSettingsService.claimPremiumEndingNotice(appUserId, end)) {
            return;
        }
        AppLanguage language = texts.languageOf(appUserId);
        String text = texts.get(language, BotTextKeys.PREMIUM_ENDING, subscriptionTexts.formatDateTime(appUserId, end),
                referralRewardService.getPlanDays(ReferralRewardService.INVITER_PLAN_CODE));
        calorieTelegramBot.sendReturnedMessage(tgUser.getTelegramId(), text, buildKeyboard(language), null);
    }

    private InlineKeyboardMarkup buildKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(premiumOfferButtons.buildExtendButton(language))
                .row(premiumOfferButtons.buildInviteButton(language))
                .row(menuButtons.buildCloseButton(language))
                .build();
    }

    @Override
    public String getSchedulerName() {
        return getClass().getSimpleName();
    }
}
