package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BookingDeleteBookingUpdateHandler implements BookingUpdateHandler{
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingService bookingService;
    private static final String RESPONSE_MSG = "Бронирование отменено";
    private static final String ERROR_MSG = "Не получилось отменить бронирование!";

    /*
    Ожидается только callback
    Обработчик отвечает за
    Отмену бронирования
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        //Отмена бронирования пользователя.
        String response = RESPONSE_MSG;
        try {
            bookingService.cancelBookingByIdOrNull(extractBookingId(data));
        } catch (RuntimeException e) {
            response = ERROR_MSG;
        }
        bookingTelegramBot.sendEditMessage(chatId, response, callbackQuery.getMessage().getMessageId(), null);
    }

    /*
    Извлечение id бронирования из данных
     */
    private Long extractBookingId(String data) {
        String[] parts = data.split(BookingTelegramBot.BOOKING_DELIMETER);
        return Long.parseLong(parts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_DELETE_BOOKING.getCommandText();
    }
}
