package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.referral.telegram.InvitePromptSender;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static com.kuklin.manageapp.bots.caloriebot.onboarding.telegram.WelcomeCalorieUpdateHandler.INSTR_URL;

/**
 * /intro — всё знакомство с ботом заново: как записывать еду и команды, канал, что дают приглашения,
 * профиль (если не заполнен). Новичку это приходит по частям: /start — как записать первое блюдо,
 * остальное — после него (FirstDishFollowUpSender). Тем же отвечает «Далее» под старыми приветствиями
 * (StartFlowCalorieUpdateHandler).
 */
@Component
@RequiredArgsConstructor
public class IntroCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final InvitePromptSender invitePromptSender;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage()) return;
        sendIntro(update.getMessage().getChatId(), telegramUser.getAppUserId());
    }

    public void sendIntro(Long chatId, Long appUserId) {
        calorieTelegramBot.sendReturnedMessage(chatId,
                texts.get(appUserId, BotTextKeys.START_FLOW_INTRO), buildIntroKeyboard(appUserId), null);
        calorieTelegramBot.sendReturnedMessage(chatId, texts.get(appUserId, BotTextKeys.START_CHANNEL));
        invitePromptSender.sendInviteRewards(chatId, appUserId);
        // Профиль — главное действие, последним сообщением
        boolean profileFilled = userNutritionProfileService.getOrCreateProfile(appUserId).checkTargetCalculateParams();
        if (!profileFilled) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(appUserId, BotTextKeys.START_FLOW_FILL_PROFILE), buildFillProfileKeyboard(appUserId), null);
        }
    }

    private InlineKeyboardMarkup buildIntroKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.COMMON_BUTTON_NEXT), Command.CALORIE_MENU.getCommandText(),
                        ButtonStyle.PRIMARY))
                .row(TelegramKeyboard.webAppButton(texts.get(appUserId, BotTextKeys.COMMON_BUTTON_INSTRUCTION), INSTR_URL))
                .row(menuButtons.buildCloseButton(appUserId))
                .build();
    }

    private InlineKeyboardMarkup buildFillProfileKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.START_FLOW_BUTTON_FILL_PROFILE),
                        Command.CALORIE_PROFILE_DIALOGUE.getCommandText(),
                        ButtonStyle.PRIMARY))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_INTRO.getCommandText();
    }
}
