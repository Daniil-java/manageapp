package com.kuklin.manageapp.bots.bookingbot.telegram.handlers.bookingprocess;

import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.entities.ConversationState;
import com.kuklin.manageapp.bots.bookingbot.entities.FormAnswer;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.services.ConversationStateService;
import com.kuklin.manageapp.bots.bookingbot.services.FormAnswerService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.bots.bookingbot.telegram.handlers.BookingUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BookingFormUpdateHandler implements BookingUpdateHandler {
    private final ConversationStateService conversationStateService;
    private final BookingTelegramBot bookingTelegramBot;
    private final FormAnswerService formAnswerService;
    private final BookingObjectService bookingObjectService;
    private final BookingService bookingService;
    private static final String ERROR_MSG = "Ошибка! Попробуйте записаться снова или выберите другое время";
    private static final String BACK_TXT = "Вернуться к календарю";
    private static final String BACK_CMD = Command.BOOKING_CALENDAR.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER + "%s";
    private static final String ERROR_GOOGLE_MSG = "Ошибка! Не получилось добавить данные в гугл-таблицу";

    @Override
    /*
    Ожидается только message
    Обработчик отвечает за
    обращение с формой
     */
    public void handle(Update update, TelegramUser telegramUser) {
        //Извлечение данных из сообщения
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String text = message.getText();

        //Получение записи бронирования пользователя, со стаусом "в процессе"
        //Предполагается существования только одной такой записи у пользователя
        Booking booking = bookingService.getBookingByUserIdAndStatusBookingOrNull(telegramUser.getTelegramId());
        if (booking == null) {
            bookingTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
            return;
        }

        //Создание и сохранение формы
        FormAnswer formAnswer = new FormAnswer()
                .setAnswer(text)
                .setBookingId(booking.getId())
                ;
        formAnswerService.save(formAnswer);

        //Изменение состояния пользователя
        conversationStateService.setConversationState(telegramUser.getTelegramId(), ConversationState.Step.COMPLETED);

        //Сохранение завершенной записи о бронировании
        booking = bookingService.addNewBookOrNull(booking.setStatus(Booking.Status.BOOKED));

        if (booking != null) {
            bookingTelegramBot.sendReturnedMessage(
                    chatId,
                    getResponseMessage(booking)
            );
        } else {
            bookingTelegramBot.sendReturnedMessage(
                    chatId,
                    ERROR_GOOGLE_MSG
            );
        }

    }

    /*
    Формирование сообщения на основе записи бронирования
    */
    private String getResponseMessage(Booking booking) {
        BookingObject bo = bookingObjectService.getByIdOrNull(booking.getBookingObjectId());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("✨ ").append(bo.getName()).append(" ✨").append("\n\n");
        sb.append("📅 ").append(booking.getStartTime().format(dateFormatter)).append("\n");
        sb.append("⏰ ")
                .append(booking.getStartTime().format(timeFormatter))
                .append(" – ")
                .append(booking.getEndTime().format(timeFormatter))
                .append("\n\n");
        sb.append("✅ Бронирование успешно создано!");

        return sb.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_FORM.getCommandText();
    }
}
