package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.dish.DishAddedEvent;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InvitePromptSender;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsRepository;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * После первого блюда — то, что раньше было в /start до него: что дают приглашения, канал и профиль
 * (норма и шкалы прогресса). Один раз за всё время (user_settings.first_dish_follow_up_at).
 * <p>
 * Как ReferralRewardNotifier: после коммита блюда, @Async — не в потоке апдейта. Отправка — через
 * {@link #DELAY_SECONDS} с: карточка блюда уходит после сохранения, сообщения не должны её обогнать.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstDishFollowUpSender {
    static final long DELAY_SECONDS = 3;

    private final UserSettingsRepository userSettingsRepository;
    private final TelegramUserService telegramUserService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final InvitePromptSender invitePromptSender;
    private final CalorieTexts texts;

    private final Executor delayedExecutor = CompletableFuture.delayedExecutor(DELAY_SECONDS, TimeUnit.SECONDS);

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onDishAdded(DishAddedEvent event) {
        Long appUserId = event.appUserId();
        try {
            if (userSettingsRepository.claimFirstDishFollowUp(appUserId, Instant.now()) == 0) {
                return;
            }
        } catch (Exception e) {
            log.error("First dish follow-up claim failed for user {}", appUserId, e);
            return;
        }
        delayedExecutor.execute(() -> sendOrLog(appUserId));
    }

    private void sendOrLog(Long appUserId) {
        try {
            TelegramUser user = telegramUserService
                    .findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT)
                    .orElse(null);
            if (user == null || Boolean.TRUE.equals(user.getIsBotBlocked())) {
                return;
            }
            send(user.getTelegramId(), appUserId);
        } catch (Exception e) {
            log.error("First dish follow-up failed for user {}", appUserId, e);
        }
    }

    void send(Long chatId, Long appUserId) {
        invitePromptSender.sendStartFlowInvite(chatId, appUserId);
        // Профиль — главное действие, последним сообщением
        boolean profileFilled = userNutritionProfileService.getOrCreateProfile(appUserId).checkTargetCalculateParams();
        String text = texts.get(appUserId, BotTextKeys.START_FIRST_DISH) + "\n\n"
                + texts.get(appUserId, BotTextKeys.START_CHANNEL);
        if (profileFilled) {
            calorieTelegramBot.sendReturnedMessage(chatId, text);
            return;
        }
        text += "\n\n" + texts.get(appUserId, BotTextKeys.START_FIRST_DISH_PROFILE);
        calorieTelegramBot.sendReturnedMessage(chatId, text, buildFillProfileKeyboard(appUserId), null);
    }

    InlineKeyboardMarkup buildFillProfileKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.START_FLOW_BUTTON_FILL_PROFILE),
                        Command.CALORIE_PROFILE_DIALOGUE.getCommandText(),
                        ButtonStyle.PRIMARY))
                .build();
    }
}
