package com.kuklin.manageapp.bots.bookingbot.telegram.handlers.bookingprocess;

import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.services.AvailabilityService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.bots.bookingbot.telegram.handlers.BookingUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCalendarUpdateHandler implements BookingUpdateHandler {
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingService bookingService;
    private final BookingObjectService bookingObjectService;
    private final AvailabilityService availabilityService;
    private final BookingObjectsUpdateHandler bookingObjectsUpdateHandler;
    private static final String RESPONSE_MSG = "\uD83D\uDCC5 Выберите дату: ";

    /*
    Ожидается только callback
    Обработчик отвечает за
    календарь. Возвращение\навигация\выбор
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        /*
        Ожидается callback команда, формата
        Command.CALENDAR.getCommandText() {bookingObjectId}
        или
        Command.CALENDAR.getCommandText() {bookingObjectId} {month}
         */

        //Разбиение пришдего update
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        //Извлечение id объекта, из пришедших данных
        Long bookingObjectId = extractBookingObjectId(data);

        //Извлечение массива данных, формата {year, month}
        int[] yearMonth = extractYearMonth(data);

        /*
        Настройка и создание календаря
        В параметры передаются месяц и год, для навигации по календарю.
        А также, комманды:
        prevCommand - для перелистывания календаря назад
        nextCommand - для перелистывания календаря вперед
        chooseCommand - для выбора конкретного дня
        backCommand - для возвращения к прошлому состоянию сообщения, до календаря

        Set<LocalDate> - даты, которые должны быть недоступны в календаре
         */

        LocalDate from = LocalDate.of(yearMonth[0], yearMonth[1], 1);
        LocalDate to   = from.withDayOfMonth(from.lengthOfMonth());

        InlineKeyboardMarkup keyboardMarkup = TelegramKeyboard.getDefaultCalendarBeforeToday(
                yearMonth[0],
                yearMonth[1],
                Command.BOOKING_CALENDAR.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER + bookingObjectId + BookingTelegramBot.BOOKING_DELIMETER,
                Command.BOOKING_CALENDAR.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER + bookingObjectId + BookingTelegramBot.BOOKING_DELIMETER,
                Command.BOOKING_BOOKTIME.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER + bookingObjectId + BookingTelegramBot.BOOKING_DELIMETER,
                bookingObjectsUpdateHandler.getHandlerListName(),
                getBookedAndNonWorksDates(bookingObjectId, from, to)
        );

        //Изменение уже существующего сообщения
        bookingTelegramBot.sendEditMessage(
                chatId,
                getResponseMsg(bookingObjectId),
                callbackQuery.getMessage().getMessageId(),
                keyboardMarkup);
    }

    private String getResponseMsg(Long bookingObjectId) {
        BookingObject bookingObject = bookingObjectService
                .getByIdOrNull(bookingObjectId);

        StringBuilder sb = new StringBuilder();
        sb.append("✨ ").append(bookingObject.getName()).append(" ✨").append("\n\n");
        sb.append(RESPONSE_MSG).append("\n");
        sb.append("━━━━━━━━━━━━━━━");

        return sb.toString();
    }

    /*
    Извлечение из данных, массива формата {year, month}
     */
    private int[] extractYearMonth(String data) {
        // {CALENDAR_CMD}{TGCLND_DELIMETER}{year}{TGCLND_DELIMETER}{month}
        if (!data.contains(TelegramKeyboard.DELIMETER)) {
            LocalDate now = LocalDate.now();
            return new int[] {now.getYear(), now.getMonthValue()};
        }
        String[] parts = data.split(TelegramKeyboard.DELIMETER);
        int year = Integer.parseInt(parts[1]);
        int month = Integer.parseInt(parts[2]);

        // Корректировка месяца
        if (month > 12) {
            year++;
            month = 1;
        } else if (month <= 0) {
            year--;
            month = 12;
        }

        return new int[] {year, month};
    }

    /*
    Получения списка недоступных дат.
    Даты могут быть недоступны из-за заданных или незаданных правил,
    а также из-за того, что доступное время в этот день закончилось
     */
    private Set<LocalDate> getBookedAndNonWorksDates(Long bookingObjectId, LocalDate from, LocalDate to) {
        List<LocalDate> bookingDates = bookingService.getReservedDates(bookingObjectId);
        List<LocalDate> nonWorkSpecificDates = availabilityService.getNonWorkingSpecificDates(bookingObjectId);
        List<DayOfWeek> nonWorkingDaysOfWeek = availabilityService.getNonWorkingDaysOfWeek(bookingObjectId);

        Set<LocalDate> dateSet = new HashSet<>();
        dateSet.addAll(bookingDates);
        dateSet.addAll(nonWorkSpecificDates);

        // Добавляем все дни недели, которые нерабочие, в заданном диапазоне
        LocalDate current = from;
        while (!current.isAfter(to)) {
            if (nonWorkingDaysOfWeek.contains(current.getDayOfWeek())) {
                dateSet.add(current);
            }
            current = current.plusDays(1);
        }

        return dateSet;
    }

    /*
    Извлечение id объекта бронирования из пришедших данных
     */
    private Long extractBookingObjectId(String data) {
        String[] dataParts = data.split(BookingTelegramBot.BOOKING_DELIMETER);
        return Long.parseLong(dataParts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_CALENDAR.getCommandText();
    }
}
