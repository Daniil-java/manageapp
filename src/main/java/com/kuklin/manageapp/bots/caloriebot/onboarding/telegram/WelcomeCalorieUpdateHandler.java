package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.web.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
public class WelcomeCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final CalorieMenuButtons menuButtons;
    public static final String INSTR_URL = CalorieSiteLinks.INSTRUCTION_URL;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update, telegramUser);
        } else if (update.hasCallbackQuery()) {
            processCallbackQuery(update, telegramUser);
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                texts.get(appUserId, BotTextKeys.WELCOME_TEXT),
                buildInstructionKeyboard(appUserId),
                null
        );
    }

    private void processCallbackQuery(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                texts.get(appUserId, BotTextKeys.WELCOME_TEXT),
                update.getCallbackQuery().getMessage().getMessageId(),
                buildInstructionKeyboard(appUserId)
        );
    }

    private InlineKeyboardMarkup buildInstructionKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.webAppButton(texts.get(appUserId, BotTextKeys.COMMON_BUTTON_INSTRUCTION), INSTR_URL,
                        ButtonStyle.PRIMARY))
                .row(menuButtons.buildBackToMenuButton(appUserId))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WELCOME.getCommandText();
    }
}
