package com.kuklin.manageapp.bots.bookingbot.telegram.handlers;

import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard.button;


@Component
@RequiredArgsConstructor
public class BookingListHandler implements BookingUpdateHandler{
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingService bookingService;
    private final BookingObjectService bookingObjectService;
    private final BookingDeleteBookingUpdateHandler bookingDeleteBookingUpdateHandler;
    private static final String CANCEL_BTN = "Отменить";
    private static final String RESPONSE_MSG = "У вас нет актуальных записей";
    /*
    Ожидается только message
    Обработчик отвечает за
    Получение списка бронирований пользователя.
    Отдельное сообщения для каждого бронирования, с кнопкой удаления записи
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();

        //Получение списка бронирований пользователя
        List<Booking> bookings = bookingService.findByTelegramUserIdAndStatus(telegramUser.getTelegramId(), Booking.Status.BOOKED);

        //Отправка отдельного сообщения для каждой брони
        for (Booking booking: bookings) {
            bookingTelegramBot.sendReturnedMessage(
                    chatId,
                    getResponseMessageText(booking),
                    getDeleteKeyboard(booking.getId()),
                    null
            );
        }

        if (bookings.isEmpty()) {
            bookingTelegramBot.sendReturnedMessage(chatId, RESPONSE_MSG);
        }
    }

    /*
    Создание клавиатуры с кнопкой удаления
     */
    private InlineKeyboardMarkup getDeleteKeyboard(Long bookingId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        String callback = bookingDeleteBookingUpdateHandler.getHandlerListName() + BookingTelegramBot.BOOKING_DELIMETER + bookingId;
        builder.row(button(CANCEL_BTN, callback));
        return builder.build();
    }

    /*
    Формирование текста ответного сообщения
     */
    private String getResponseMessageText(Booking booking) {
        BookingObject bookingObject = bookingObjectService.getByIdOrNull(booking.getBookingObjectId());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("✨ ").append(bookingObject.getName()).append(" ✨").append("\n\n");
        sb.append("📅 ").append(booking.getStartTime().format(dateFormatter)).append("\n");
        sb.append("⏰ ")
                .append(booking.getStartTime().format(timeFormatter))
                .append(" – ")
                .append(booking.getEndTime().format(timeFormatter)).append("\n\n");
        sb.append("✅ Ваша бронь подтверждена!");

        return sb.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_MYLIST.getCommandText();
    }
}
