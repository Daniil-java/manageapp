package com.kuklin.manageapp.bots.bookingbot.services;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.kuklin.manageapp.bots.bookingbot.entities.Booking;
import com.kuklin.manageapp.bots.bookingbot.entities.BookingObject;
import com.kuklin.manageapp.bots.bookingbot.entities.FormAnswer;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final Sheets sheets;
    private final BookingObjectService bookingObjectService;
    private final FormAnswerService formAnswerService;
    private final TelegramUserService telegramUserService;
    private static final String SPREADSHEET_ID = "1ew0iEiY6Otvn8jD2l2vmBmxw5hUDMGJ1nW6gXdLqFqk";
    private static final String RANGE = "Бронирование"; // Название листа

    public void appendBooking(Booking booking) throws IOException {
        TelegramUser telegramUser = telegramUserService
                .getTelegramUserByTelegramIdAndBotIdentifierOrNull(
                        booking.getTelegramUserId(), BotIdentifier.BOOKING_BOT);

        BookingObject bookingObject = bookingObjectService.getByIdOrNull(booking.getBookingObjectId());
        FormAnswer formAnswer = formAnswerService.findByBookingIdOrNull(booking.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        //Обрезка текста из формы, для безопасности от плохих слов
        String openText = formAnswer.getAnswer();
        if (openText != null && openText.codePointCount(0, openText.length()) > 5) {
            int end = openText.offsetByCodePoints(0, 5);
            openText = openText.substring(0, end) + "[ДОСТУПНО АДМИНИСТРАТОРУ]";
        }

        List<Object> row = List.of(
                booking.getId(),
                bookingObject.getName(),
                telegramUser.getFirstname(),
                openText,
                booking.getStartTime().format(formatter),
                booking.getEndTime().format(formatter),
                booking.getStatus().name(),
                booking.getCreatedAt().format(formatter),
                booking.getUpdatedAt().format(formatter)
        );

        ValueRange body = new ValueRange().setValues(List.of(row));

        sheets.spreadsheets().values()
                .append(SPREADSHEET_ID, RANGE, body)
                .setValueInputOption("RAW")
                .execute();

        int rowIndex = getLastRowIndex();
        applyStatusColor(booking.getStatus(), rowIndex);
    }

    public void updateBookingStatus(Long bookingId, Booking.Status newStatus) throws IOException {
        // 1. Читаем все строки
        ValueRange response = sheets.spreadsheets().values()
                .get(SPREADSHEET_ID, RANGE)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return;

        // 2. Ищем строку по ID (в колонке A)
        int rowIndex = -1;
        for (int i = 1; i < values.size(); i++) { // начинаем с 1, т.к. 0 — заголовки
            if (values.get(i).size() > 0 && values.get(i).get(0).toString().equals(bookingId.toString())) {
                rowIndex = i + 1; // +1, т.к. индексация в A1 начинается с 1
                break;
            }
        }

        if (rowIndex == -1) return; // не нашли

        // 3. Обновляем колонку G (статус)
        String range = RANGE + "!G" + rowIndex;

        ValueRange body = new ValueRange().setValues(List.of(List.of(newStatus.name())));

        sheets.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("RAW")
                .execute();

        // красим ячейку
        applyStatusColor(newStatus, rowIndex);
    }

    // === ПОДСВЕТКА ЯЧЕЙКИ СО СТАТУСОМ ===
    private void applyStatusColor(Booking.Status status, int rowIndex) throws IOException {
        Color color = switch (status) {
            case BOOKING -> new Color().setRed(1f).setGreen(1f).setBlue(0f);   // жёлтый
            case BOOKED -> new Color().setRed(0f).setGreen(1f).setBlue(0f);   // зелёный
            case CANCELLED -> new Color().setRed(1f).setGreen(0f).setBlue(0f);   // красный
            case COMPLETED -> new Color().setRed(0.6f).setGreen(0.6f).setBlue(0.6f); // серый
        };

        RepeatCellRequest repeatCellRequest = new RepeatCellRequest()
                .setRange(new GridRange()
                        .setSheetId(0) // ID листа (обычно 0 для первого)
                        .setStartRowIndex(rowIndex - 1) // индексация с 0
                        .setEndRowIndex(rowIndex)
                        .setStartColumnIndex(6) // колонка G → индекс 6
                        .setEndColumnIndex(7))
                .setCell(new CellData().setUserEnteredFormat(
                        new CellFormat().setBackgroundColor(color)))
                .setFields("userEnteredFormat.backgroundColor");

        BatchUpdateSpreadsheetRequest formatRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new Request().setRepeatCell(repeatCellRequest)));

        sheets.spreadsheets().batchUpdate(SPREADSHEET_ID, formatRequest).execute();
    }

    // === ВСПОМОГАТЕЛЬНЫЙ МЕТОД: индекс последней строки ===
    private int getLastRowIndex() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(SPREADSHEET_ID, RANGE)
                .execute();
        List<List<Object>> values = response.getValues();
        return values == null ? 1 : values.size(); // +1 не нужен, т.к. size уже даёт последнюю строку
    }

}
