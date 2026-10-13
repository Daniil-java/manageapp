package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class CalorieCloseButtonCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        calorieTelegramBot.sendDeleteMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                update.getCallbackQuery().getMessage().getMessageId()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_CLOSE.getCommandText();
    }
}
