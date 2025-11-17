package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StartUpdateHandler implements CalorieBotUpdateHandler {

    private static final String START_MESSAGE =
            """
                    Отправь фото блюда, напиши его описание или отправь голосовое сообщение, чтобы получить КБЖУ блюда!
                    """;
    private final CalorieTelegramBot calorieTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE,
                getCommandKeyboard(),
                null
        );
    }

    public static ReplyKeyboardMarkup getCommandKeyboard() {
        ReplyKeyboardMarkup replyKeyboard = new ReplyKeyboardMarkup();
        replyKeyboard.setResizeKeyboard(true);
        replyKeyboard.setOneTimeKeyboard(false);

        KeyboardRow row = new KeyboardRow();
        row.add(Command.CALORIE_TODAY_LIST.getCommandText());
        row.add(Command.CALORIE_WEEK_LIST.getCommandText());

        replyKeyboard.setKeyboard(List.of(row));
        return replyKeyboard;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
