package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
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
    private static final String UPDATE_MESSAGE =
            """
                    ⏳ Обновляю статус
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

        KeyboardRow row1 = new KeyboardRow();
        row1.add(Command.CALORIE_SETTINGS.getCommandText());
        row1.add(Command.CALORIE_WATER.getCommandText());

        KeyboardRow row = new KeyboardRow();
        row.add(Command.CALORIE_PROFILE.getCommandText());
        row.add(Command.CALORIE_FAVORITE.getCommandText());

        KeyboardRow nextRow = new KeyboardRow();
        nextRow.add(Command.CALORIE_TODAY_LIST.getCommandText());
        nextRow.add(Command.CALORIE_WEEK_LIST.getCommandText());

        replyKeyboard.setKeyboard(List.of(row1, row, nextRow));
        return replyKeyboard;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
