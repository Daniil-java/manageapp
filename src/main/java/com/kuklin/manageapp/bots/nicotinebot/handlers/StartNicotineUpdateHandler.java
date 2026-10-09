package com.kuklin.manageapp.bots.nicotinebot.handlers;

import com.kuklin.manageapp.bots.nicotinebot.NicotineTelegramBot;
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
public class StartNicotineUpdateHandler implements NicotineUpdateHandler{
    private final NicotineTelegramBot nicotineTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String msg =
                """
                        %s - начал курить
                        %s - время последнего курения
                        %s - список прокуренного задень
                        """;

        nicotineTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                String.format(msg,
                        Command.NICOTINE_MAKE_NEW.getCommandText(),
                        Command.NICOTINE_GET_LAST.getCommandText(),
                        Command.NICOTINE_TODAY.getCommandText()
                ),
                getKeyboard(),
                null
        );

    }

    public static ReplyKeyboardMarkup getKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(Command.NICOTINE_GET_LAST.getCommandText());
        KeyboardRow rowA = new KeyboardRow();
        rowA.add(Command.NICOTINE_MAKE_NEW.getCommandText());
        KeyboardRow rowB = new KeyboardRow();
        rowB.add(Command.NICOTINE_TODAY.getCommandText());

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(List.of(row, rowA, rowB));
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        return keyboard;
    }

    @Override
    public String getHandlerListName() {
        return Command.NICOTINE_START.getCommandText();
    }
}
