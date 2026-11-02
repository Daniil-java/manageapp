package com.kuklin.manageapp.bots.bookingbot.telegram.handlers.bookingprocess;

import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.services.BookingObjectService;
import com.kuklin.manageapp.bots.bookingbot.telegram.BookingTelegramBot;
import com.kuklin.manageapp.bots.bookingbot.telegram.handlers.BookingUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingObjectsUpdateHandler implements BookingUpdateHandler {
    private final BookingTelegramBot bookingTelegramBot;
    private final BookingObjectService bookingObjectService;
    private static final String RESPONSE_MSG = "Выберите объект:";
    //Команда навигации календаря
    public static final String PREV_CMD = Command.BOOKING_BOOKINGOBJECT.getCommandText() + " /prev ";
    //Команда навигации календаря
    public static final String NEXT_CMD = Command.BOOKING_BOOKINGOBJECT.getCommandText() + " /next ";
    //Количество строк в списке (в строке - 1 элемент)
    public static final Integer ROW_COUNT = 8;
    //Команда зашифрованная в сообщение, для переключения на следующий обработчик
    public static final String NEXT_HANDLER_CMD = Command.BOOKING_CALENDAR.getCommandText() + BookingTelegramBot.BOOKING_DELIMETER;

    /*
    Ожидается или message, или callback
    Обработчик отвечает за
    возврат клавиатуры-списка доступных к бронированию объектов
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        //Получение страницы из пришедших данных (message или callbackdata)
        int page = extractPage(update);

        //Получение объектов, которые можно забронировать
        List<BookingObject> bookingObjects =
                bookingObjectService.getAllBookingObject(page, ROW_COUNT);

        //Получение клавиатуры со списком объектов, с заданной страницей и кол-вом строк
        InlineKeyboardMarkup keyboardMarkup =
                getBookingObjectListKeyboard(bookingObjects, ROW_COUNT, page);

        //Отправка нового или изменение страго сообщения
        sendBookingObjectsList(update, keyboardMarkup);
    }

    /*
    Отправляет новое или изменяет старое сообщение,
    в зависимости от пришедшего типа данных.
     */
    private void sendBookingObjectsList(Update update, InlineKeyboardMarkup keyboardMarkup) {
        if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            bookingTelegramBot.sendEditMessage(
                    chatId,
                    RESPONSE_MSG,
                    update.getCallbackQuery().getMessage().getMessageId(),
                    keyboardMarkup
            );
        } else {
            Long chatId =  update.getMessage().getChatId();
            bookingTelegramBot.sendReturnedMessage(
                    chatId,
                    RESPONSE_MSG,
                    keyboardMarkup,
                    null
            );
        }
    }

    /*
    Извлекает номер страницы, из пришедших данных (message или callback).
    Если страница не указана, то по дефолту - 0.
     */
    private int extractPage(Update update) {
        int page = 0;
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (data.equals(getHandlerListName())) {
                return page;
            }
            page = Integer.parseInt(
                    data.substring(data.startsWith(PREV_CMD)
                            ? PREV_CMD.length()
                            : NEXT_CMD.length())
            );
        }
        return page;
    }

    /*
    Создание клавиатуры-списка объектов
     */
    public InlineKeyboardMarkup getBookingObjectListKeyboard(
            List<BookingObject> bookingObjects, int rowCount, int page
    ) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        // Кнопки вакансий
        for (BookingObject bookingObject : bookingObjects) {
            builder.row(
                    TelegramKeyboard.button(
                            bookingObject.getName(),
                            NEXT_HANDLER_CMD + bookingObject.getId()
                    )
            );
        }

        // Кнопки навигации
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 0) {
            InlineKeyboardButton prev = TelegramKeyboard.button("⬅️", PREV_CMD + (page - 1));
            navRow.add(prev);
        }
        if (bookingObjects.size() == rowCount) {
            InlineKeyboardButton next = TelegramKeyboard.button("➡️", NEXT_CMD + (page + 1));
            navRow.add(next);
        }
        if (!navRow.isEmpty()) {
            builder.rows(navRow);
        }

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.BOOKING_BOOKINGOBJECT.getCommandText();
    }
}
