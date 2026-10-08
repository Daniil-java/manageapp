package com.kuklin.manageapp.bots.bookingbot.telegram.handlers.bookingprocess;

import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.entities.ConversationState;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.services.ConversationStateService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BookingUpdateHandler implements com.kuklin.manageapp.bots.bookingbot.telegram.handlers.BookingUpdateHandler {
    private final BookingService bookingService;
    private final ConversationStateService conversationStateService;
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingObjectService bookingObjectService;
    private static final String RESPONSE_MSG = "✍ Введите ваше имя и способ связи с вами.";
    private static final String BACK_TXT = "Вернуться к календарю";
    private static final String BACK_CMD = Command.BOOKING_CALENDAR.getCommandText() + TelegramBot.DEFAULT_DELIMETER + "%s";

    /*
    Ожидается только callback
    Обработчик отвечает за
    Создание записи о бронировании, со стасом в "процессе бронирования"
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Разбиение update на данные
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        //Извлечение id объекта бронирования из пришедших данных
        Long bookingObjectId = extractBookingObjectId(data);
        //Извлечение времени начала и конца интервала из пришедших данных
        LocalDateTime[] localDateTimes = extractLocalDate(data);

        //Создание записи о бронировании
        Booking booking = new Booking()
                .setBookingObjectId(bookingObjectId)
                .setTelegramUserId(telegramUser.getTelegramId())
                .setStatus(Booking.Status.BOOKING)
                .setStartTime(localDateTimes[0])
                .setEndTime(localDateTimes[1])
                ;
        //Предполагается, что у пользователя только один объект может быть в процессе бронирования
        bookingService.saveAndUnbookOtherBookProcessing(booking);

        //Изменение состояния пользователя, для заполениния формы
        conversationStateService.setConversationState(
                telegramUser.getTelegramId(), ConversationState.Step.FILL_FORM
        );

        bookingTelegramBot.sendEditMessage(
                chatId,
                getResponseMsg(bookingObjectId, localDateTimes),
                callbackQuery.getMessage().getMessageId(),
                getBackButton(BACK_TXT, String.format(BACK_CMD, bookingObjectId))
                );
    }

    private String getResponseMsg(Long bookingObjectId, LocalDateTime[] localDate) {
        BookingObject bookingObject = bookingObjectService
                .getByIdOrNull(bookingObjectId);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("✨ ").append(bookingObject.getName()).append(" ✨").append("\n\n");
        sb.append("📅 ").append(localDate[0].format(dateFormatter)).append("\n");
        sb.append("⏰ ").append(localDate[0].format(timeFormatter))
                .append(" – ").append(localDate[1].format(timeFormatter)).append("\n\n");
        sb.append(RESPONSE_MSG).append("\n");
        sb.append("━━━━━━━━━━━━━━━");

        return sb.toString();
    }

    /*
    Создание клавиатуры с кнопкой "Назад"
    */
    private InlineKeyboardMarkup getBackButton(String text, String data) {
        return TelegramKeyboard.getSingleButtonKeyboard(text, data);
    }

    /*
    Извлечение id бронируемого объекта, из данных
     */
    private Long extractBookingObjectId(String data) {
        String[] parts = data.split(BookingTelegramBot.BOOKING_DELIMETER);
        return Long.parseLong(parts[1]);
    }

    /*
    Извлечение времени начала и конца интервала
     */
    private LocalDateTime[] extractLocalDate(String data) {
        //bookingUpdateHandler.getHandlerListName() + " " + bookingObjectId + " " + DELIMETER + localDate + DELIMETER + label;
        String[] parts = data.split(TelegramKeyboard.DELIMETER);
        // parts[0] = handler name
        // parts[1] = дата (например, "2025-09-30")
        // parts[2] = интервал времени (например, "09:00–09:30")

        LocalDate date = LocalDate.parse(parts[1]);
        String[] timeRange = parts[2].split("–");

        LocalTime startTime = LocalTime.parse(timeRange[0]);
        LocalTime endTime = LocalTime.parse(timeRange[1]);

        LocalDateTime start = date.atTime(startTime);
        LocalDateTime end = date.atTime(endTime);

        return new LocalDateTime[] {start, end};
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_BOOK.getCommandText();
    }
}
