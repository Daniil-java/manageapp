package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BookingStartUpdateHandler implements BookingUpdateHandler {

    private static final String START_MESSAGE =
            """
                    <b>Бот для бронирования (пример)</b>
                    Таблица записей
                    https://docs.google.com/spreadsheets/d/1ew0iEiY6Otvn8jD2l2vmBmxw5hUDMGJ1nW6gXdLqFqk/edit?gid=0#gid=0
                    """;

    private final BookingTelegramBot bookingTelegramBot;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        bookingTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                START_MESSAGE
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_START.getCommandText();
    }
}
