package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class KeyboardCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        updateKeyboard(update.getMessage().getChatId(), telegramUser.getAppUserId());
    }

    public void updateKeyboard(Long chatId, Long appUserId) {
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(appUserId, BotTextKeys.KEYBOARD_UPDATED),
                menuButtons.buildKeyboard(appUserId),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_KEYBOARD.getCommandText();
    }
}
