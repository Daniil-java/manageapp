package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.telegram.common.CalorieBotUpdateHandler;
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
public class KeyboardCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        updateKeyboard(update.getMessage().getChatId());
    }

    public void updateKeyboard(Long chatId) {
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                "Клавиатура обновлена",
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
        settingsRow.add(Command.CALORIE_PROFILE.getCommandText());

        KeyboardRow profileRow = new KeyboardRow();
        profileRow.add(Command.CALORIE_WATER.getCommandText());
        profileRow.add(Command.CALORIE_FAVORITE.getCommandText());

        KeyboardRow statisticsRow = new KeyboardRow();
        statisticsRow.add(Command.CALORIE_TODAY_LIST.getCommandText());

        KeyboardRow reportRow = new KeyboardRow();
        reportRow.add(Command.CALORIE_REPORT.getCommandText());
        reportRow.add(Command.CALORIE_WELCOME.getCommandText());

        KeyboardRow subRow = new KeyboardRow();
        subRow.add(Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText());
        subRow.add(Command.CALORIE_UTM.getCommandText());

        // Собираем в список в том порядке, в котором они должны идти в интерфейсе
        markup.setKeyboard(List.of(
                statisticsRow,
                profileRow,
                settingsRow,
                reportRow,
                subRow
        ));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_KEYBOARD.getCommandText();
    }
}
