package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private static final String START_MESSAGE =
            """
                    Отправь фото блюда, напиши его описание или отправь голосовое сообщение, чтобы получить КБЖУ блюда!
                    /today - получить дневник питания за сегодня
                    """;
    private final CalorieTelegramBot calorieTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), START_MESSAGE);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
