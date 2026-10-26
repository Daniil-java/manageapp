package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.InvitePromptSender;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.welcome.WelcomeCalorieUpdateHandler.INSTR_URL;

@Component
@RequiredArgsConstructor
public class StartFlowCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieTexts texts;
    private final InvitePromptSender invitePromptSender;
    private final CalorieMenuButtons menuButtons;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        Long appUserId = telegramUser.getAppUserId();
        boolean isFilledProfile = userNutritionProfileService
                .getOrCreateProfile(appUserId)
                .checkTargetCalculateParams();

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        // Приветствие (со ссылками на политику и соглашение) остаётся, убираем только «Далее» / «Закрыть»
        calorieTelegramBot.sendEditMessageReplyMarkupNull(
                chatId,
                update.getCallbackQuery().getMessage().getMessageId()
        );

        calorieTelegramBot.sendReturnedMessage(chatId,
                texts.get(appUserId, BotTextKeys.START_FLOW_INTRO), getStartFlowKeyboard(appUserId), null);

        // Что дают приглашения — один раз, в начале; дальше напоминание не чаще раза в неделю (InvitePromptSender).
        // Перед шагом профиля: он — главное действие и должен остаться последним сообщением
        invitePromptSender.sendStartFlowInvite(chatId, appUserId);

        // Пришёл с лендинга с данными калькулятора — профиль уже заполнен, шаг не нужен.
        // Отдельным сообщением: анкета профиля редактирует сообщение с кнопкой — инструкция выше остаётся в чате
        if (!isFilledProfile) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(appUserId, BotTextKeys.START_FLOW_FILL_PROFILE), getFillProfileKeyboard(appUserId), null);
        }
    }

    private InlineKeyboardMarkup getFillProfileKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.START_FLOW_BUTTON_FILL_PROFILE),
                        Command.CALORIE_PROFILE_DIALOGUE.getCommandText(),
                        ButtonStyle.PRIMARY))
                .build();
    }

    private InlineKeyboardMarkup getStartFlowKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.COMMON_BUTTON_NEXT), Command.CALORIE_MENU.getCommandText(),
                        ButtonStyle.PRIMARY))
                .row(TelegramKeyboard.webAppButton(texts.get(appUserId, BotTextKeys.COMMON_BUTTON_INSTRUCTION), INSTR_URL))
                .row(menuButtons.buildCloseButton(appUserId))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START_FLOW_1.getCommandText();
    }
}
