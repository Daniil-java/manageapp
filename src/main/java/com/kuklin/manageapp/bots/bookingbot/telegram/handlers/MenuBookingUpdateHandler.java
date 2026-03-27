package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
public class MenuBookingUpdateHandler implements BookingUpdateHandler {
    private final BookingTelegramBot bookingTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            bookingTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    "Выберите действие \uD83D\uDC47",
                    getMenuKeyboard(),
                    null
            );
        }

    }

    private InlineKeyboardMarkup getMenuKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder =
                TelegramKeyboard.builder();

        builder
                .row(
                        TelegramKeyboard.button(Command.BOOKING_BOOKINGOBJECT.getCommandText(), Command.BOOKING_BOOKINGOBJECT.getCommandText())
                ).row(
                        TelegramKeyboard.button(Command.BOOKING_MYLIST.getCommandText(), Command.BOOKING_MYLIST.getCommandText())
                );

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_MENU.getCommandText();
    }
}
