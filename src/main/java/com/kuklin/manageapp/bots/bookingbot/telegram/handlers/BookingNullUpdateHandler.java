package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNullUpdateHandler implements BookingUpdateHandler{
    private final BookingTelegramBot telegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            try {
                telegramBot.execute(AnswerCallbackQuery.builder()
                        .text("")
                        .callbackQueryId(update.getCallbackQuery().getId())
                        .build());
            } catch (TelegramApiException e) {
                log.error("Неправильный формат сообщения");
            }
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_NULL.getCommandText();
    }
}
