package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.welcome;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;

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
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(texts.get(appUserId, BotTextKeys.COMMON_BUTTON_INSTRUCTION));
        button.setWebApp(new WebAppInfo(INSTR_URL));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(
                List.of(TelegramKeyboard.withStyle(button, ButtonStyle.PRIMARY)),
                List.of(menuButtons.buildBackToMenuButton(appUserId))));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WELCOME.getCommandText();
    }
}
