package com.kuklin.manageapp.bots.caloriebot.telegram;

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

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        updateKeyboard(update.getMessage().getChatId(), telegramUser.getAppUserId());
    }

    public void updateKeyboard(Long chatId, Long appUserId) {
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                "Клавиатура обновлена",
                menuButtons.buildKeyboard(appUserId),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_KEYBOARD.getCommandText();
    }
}
