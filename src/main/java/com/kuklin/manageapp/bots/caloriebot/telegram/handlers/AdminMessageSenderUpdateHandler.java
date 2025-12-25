package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Оповещение всех пользователей
 */
@Component
@RequiredArgsConstructor
public class AdminMessageSenderUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramUserService telegramUserService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!telegramUser.getTelegramId().equals(425120436L)) return;

        for (TelegramUser tgUser: telegramUserService.getAllTelegramUsersByBotIdentifierOrNull(BotIdentifier.CALORIE_BOT)) {
            calorieTelegramBot.sendReturnedMessage(
                    tgUser.getTelegramId(),
                    update.getMessage().getText().substring(getHandlerListName().length()),
                    StartUpdateHandler.getCommandKeyboard(),
                    null
            );
        }

    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_ADMIN_MESSAGE.getCommandText();
    }
}
