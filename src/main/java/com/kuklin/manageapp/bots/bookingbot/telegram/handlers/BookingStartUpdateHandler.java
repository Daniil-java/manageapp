package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
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
public class BookingStartUpdateHandler implements BookingUpdateHandler {

    private static final String START_MESSAGE =
            """
                    <b>Бот для бронирования (пример)</b>
                    Таблица записей
                    https://docs.google.com/spreadsheets/d/1ew0iEiY6Otvn8jD2l2vmBmxw5hUDMGJ1nW6gXdLqFqk/edit?gid=0#gid=0
                    
                    Используйте кнопки в клавиатуре или по команде /menu
                    """;

    private final BookingTelegramBot bookingTelegramBot;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        bookingTelegramBot.sendReturnedMessage(
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
        settingsRow.add(Command.BOOKING_BOOKINGOBJECT.getCommandText());
        settingsRow.add(Command.BOOKING_MYLIST.getCommandText());

        // Собираем в список в том порядке, в котором они должны идти в интерфейсе
        markup.setKeyboard(List.of(settingsRow));

        return markup;
    }


    @Override
    public String getHandlerListName() {
        return Command.BOOKING_START.getCommandText();
    }
}
