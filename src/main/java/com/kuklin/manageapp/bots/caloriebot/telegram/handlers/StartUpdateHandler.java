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
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        KeyboardRow settingsRow = new KeyboardRow();
        settingsRow.add(Command.CALORIE_SETTINGS.getCommandText());
        settingsRow.add(Command.CALORIE_WATER.getCommandText());

        KeyboardRow profileRow = new KeyboardRow();
        profileRow.add(Command.CALORIE_PROFILE.getCommandText());
        profileRow.add(Command.CALORIE_FAVORITE.getCommandText());

        KeyboardRow statisticsRow = new KeyboardRow();
        statisticsRow.add(Command.CALORIE_TODAY_LIST.getCommandText());
        statisticsRow.add(Command.CALORIE_WEIGHT_HISTORY.getCommandText());

        KeyboardRow reportRow = new KeyboardRow();
        reportRow.add(Command.CALORIE_REPORT.getCommandText());

        // Собираем в список в том порядке, в котором они должны идти в интерфейсе
        markup.setKeyboard(List.of(
                settingsRow,
                profileRow,
                statisticsRow,
                reportRow
        ));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START.getCommandText();
    }
}
