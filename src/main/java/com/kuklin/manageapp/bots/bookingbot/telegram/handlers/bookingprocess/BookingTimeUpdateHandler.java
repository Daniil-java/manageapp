package com.kuklin.manageapp.bots.bookingbot.telegram.handlers.bookingprocess;

import com.kuklin.manageapp.bots.bookingbot.entities.AvailabilityRule;
import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.services.AvailabilityService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.services.BookingService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard.DELIMETER;
import static com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard.button;


@Component
@RequiredArgsConstructor
public class BookingTimeUpdateHandler implements com.kuklin.manageapp.bots.bookingbot.telegram.handlers.BookingUpdateHandler {
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingUpdateHandler bookingUpdateHandler;
    private final BookingObjectService bookingObjectService;
    private static final String RESPONSE_MSG = "У вас есть 10 минут, чтобы выбрать время \n⏰Выберите время: ";
    private static final String BACK_TXT = "Назад";
    private static final String BACK_CMD = Command.BOOKING_CALENDAR.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER + "%s";

    /*
    Ожидается только callback
    Обработчик отвечает за
    выбор интервала времени, в конкретный день
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Разбиение update на данные
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();

        //Извлечение id бронируемого-объекта из данных
        Long bookingObjectId = extractBookingObjecctId(data);

        //Излвечение из данных, массива формата {year, month, day}
        int[] yearMonthDay = extractYearMonthDay(data);
        //Получение даты форматы LocalDate
        LocalDate localDate = LocalDate.of(yearMonthDay[0], yearMonthDay[1], yearMonthDay[2]);
        //Получение конкретного дня формата DayOfWeek, из конкретной даты
        DayOfWeek dayOfWeek = localDate.getDayOfWeek();

        /*
        Получение специфических правил, актуальных для конкретного объекта.
        Правил может быть несколько. Они могут друг друга перекрывать.
        Специфические правила важнее обычных.
         */
        List<AvailabilityRule> rules = availabilityService.getAllBySpecificDate(localDate);

        //Если нет специфических правил - находим обычные правила
        if (rules.isEmpty()) {
            //Получение обычных правил
            //Подразумевается, если день не рабочий, то до сюда дойти не должно
            rules = availabilityService.getAllByDayOfWeek(dayOfWeek);
        }

        //Генерация списка свободных интервалов на основе правил и уже забронированных слотов
        List<LocalTime[]> slots = processDates(rules, bookingObjectId, localDate);

        bookingTelegramBot.sendEditMessage(
                chatId,
                getResponseMsg(bookingObjectId, localDate),
                messageId,
                        //Создание клавиатуры со свободными интервалами.
                getTimeSlotsKeyboard(slots, localDate, bookingObjectId)
        );
    }

    private String getResponseMsg(Long bookingObjectId, LocalDate localDate) {
        BookingObject bookingObject = bookingObjectService
                .getByIdOrNull(bookingObjectId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

        StringBuilder sb = new StringBuilder();
        sb.append("✨ ").append(bookingObject.getName()).append(" ✨").append("\n\n");
        sb.append("📅 ").append(localDate.format(formatter)).append("\n\n");
        sb.append(RESPONSE_MSG).append("\n");
        sb.append("━━━━━━━━━━━━━━━");

        return sb.toString();
    }

    /*
    Генерация списка свободных интервалов на основе правил
     */
    private List<LocalTime[]> processDates(List<AvailabilityRule> specificRules, Long bookingObjectId, LocalDate localDate) {
        // 1. Определяем общее рабочее время
        LocalTime minStart = specificRules.stream()
                .map(AvailabilityRule::getStartTime)
                .min(LocalTime::compareTo)
                .orElse(null);

        LocalTime maxEnd = specificRules.stream()
                .map(AvailabilityRule::getEndTime)
                .max(LocalTime::compareTo)
                .orElse(null);

        // 2. Определяем шаг слота (берём самый большой)
        int slotMinutes = specificRules.stream()
                .map(AvailabilityRule::getSlotDurationMinutes)
                .max(Integer::compare)
                .orElse(AvailabilityRule.DEFAULT_INTERVAL); // дефолт

        // 3. Генерация слотов, с учетом уже забронированных интервалов
        return bookingService.getAvailableSlots(bookingObjectId, localDate, minStart, maxEnd, slotMinutes);
    }


    /*
    Извлечение id бронируемого объекта из данных
     */
    private Long extractBookingObjecctId(String data) {
        String[] parts = data.split(BookingTelegramBot.BOOKING_DELIMETER);
        return Long.parseLong(parts[1]);
    }

    /*
    Излвечение из данных, массива формата {year, month, day}
     */
    private int[] extractYearMonthDay(String data) {
        //Ожидаются данные, формата
        //chooseCommand + DATE + DELIMETER + year + DELIMETER + month + DELIMETER + day
        String[] parts = data.split(DELIMETER);

        Integer year = Integer.parseInt(parts[1]);
        Integer month = Integer.parseInt(parts[2]);
        Integer day = Integer.parseInt(parts[3]);

        return new int[] {year, month, day};
    }

    /*
    Создание клавиатуры со свободными интервалами.
     */
    private InlineKeyboardMarkup getTimeSlotsKeyboard(
            List<LocalTime[]> slots, LocalDate localDate, Long bookingObjectId
    ) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        int perRow = 3; // сколько кнопок в ряд
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < slots.size(); i++) {
            LocalTime[] slot = slots.get(i);
            String label = slot[0] + "–" + slot[1]; // например "09:00–09:30"
            String callbackData = bookingUpdateHandler.getHandlerListName() + BookingTelegramBot.BOOKING_DELIMETER + bookingObjectId + BookingTelegramBot.BOOKING_DELIMETER + DELIMETER + localDate + DELIMETER + label;

            currentRow.add(button(label, callbackData));

            if (currentRow.size() == perRow) {
                builder.row(currentRow);
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            builder.row(currentRow);
        }
        builder.row(BACK_TXT, String.format(BACK_CMD, bookingObjectId));

        return builder.build();

    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_BOOKTIME.getCommandText();
    }
}
