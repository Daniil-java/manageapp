package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.repository.UserSettingsRepository;
import com.kuklin.manageapp.bots.caloriebot.components.services.ReferralRewardService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.DayCardCalorieUpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Предложение пригласить друга — так, чтобы не стало спамом:
 * <ul>
 *     <li>в старт-флоу — один раз, сразу с условиями наград;</li>
 *     <li>после вечернего отчёта (день с блюдами — момент, когда человек доволен) — не раньше чем через
 *     {@link #MIN_ACCOUNT_AGE} после прихода и не чаще раза в {@link #PROMPT_INTERVAL}, считая и старт-флоу.</li>
 * </ul>
 * Время последнего предложения — user_settings.invite_prompt_last_at; проверка и отметка одним UPDATE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitePromptSender {

    static final Duration PROMPT_INTERVAL = Duration.ofDays(7);
    static final Duration MIN_ACCOUNT_AGE = Duration.ofDays(3);

    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSettingsRepository userSettingsRepository;
    private final UserSettingsService userSettingsService;
    private final ReferralRewardService referralRewardService;
    private final InviteLinks inviteLinks;
    private final CalorieTexts texts;

    /** Старт-флоу: что дают приглашения — один раз за всё время; следующее предложение — не раньше чем через неделю. */
    public void sendStartFlowInvite(Long chatId, Long appUserId) {
        if (userSettingsRepository.claimFirstInvitePrompt(appUserId, Instant.now()) == 0) {
            return;
        }
        String text = texts.get(appUserId, BotTextKeys.INVITE_START_FLOW,
                referralRewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE),
                referralRewardService.getPlanDays(ReferralRewardService.INVITER_PLAN_CODE),
                referralRewardService.getPlanDays(ReferralRewardService.FRIEND_PAID_PLAN_CODE));
        InlineKeyboardMarkup keyboard = TelegramKeyboard.builder()
                .row(inviteLinks.buildShareButton(appUserId))
                .row(inviteLinks.buildDetailsButton(appUserId))
                .build();
        calorieTelegramBot.sendReturnedMessage(chatId, text, keyboard, null);
    }

    /**
     * После вечернего отчёта: «поделись днём или позови друга», если пора (см. класс). Сбой не мешает отчёту.
     *
     * @return true — отправлено
     */
    public boolean sendAfterDailyReportIfDue(Long chatId, Long appUserId) {
        try {
            Instant now = Instant.now();
            int claimed = userSettingsRepository.claimInvitePrompt(appUserId, now,
                    now.minus(MIN_ACCOUNT_AGE), now.minus(PROMPT_INTERVAL));
            if (claimed == 0) {
                return false;
            }
            String text = texts.get(appUserId, BotTextKeys.INVITE_PROMPT,
                    referralRewardService.getPlanDays(ReferralRewardService.INVITED_PLAN_CODE),
                    referralRewardService.getPlanDays(ReferralRewardService.INVITER_PLAN_CODE));
            // Карточка за день отчёта — даже если нажать на следующий день
            LocalDate reportDay = LocalDate.now(userSettingsService.getZoneId(appUserId));
            InlineKeyboardMarkup keyboard = TelegramKeyboard.builder()
                    .row(TelegramKeyboard.button(texts.get(appUserId, BotTextKeys.CARD_BUTTON),
                            DayCardCalorieUpdateHandler.buildCallbackData(reportDay), ButtonStyle.PRIMARY))
                    .row(inviteLinks.buildShareButton(appUserId))
                    .build();
            calorieTelegramBot.sendReturnedMessage(chatId, text, keyboard, null);
            return true;
        } catch (Exception e) {
            log.error("Invite prompt failed for user {}", appUserId, e);
            return false;
        }
    }
}
