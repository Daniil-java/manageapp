package com.kuklin.manageapp.bots.aiassistantcalendar.services;

import com.kuklin.manageapp.bots.aiassistantcalendar.entities.UserMessagesLog;
import com.kuklin.manageapp.bots.aiassistantcalendar.repositories.UserMessagesLogRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserMessagesLogExportService {
    private final UserMessagesLogRepository repository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] exportAllAsXlsx() throws IOException {
        // Все записи, новые сверху
        List<UserMessagesLog> logs = repository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("User messages log");

            int rowIdx = 0;

            // Заголовок
            Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("telegram_id");
            header.createCell(2).setCellValue("username");
            header.createCell(3).setCellValue("firstname");
            header.createCell(4).setCellValue("lastname");
            header.createCell(5).setCellValue("google_email");
            header.createCell(6).setCellValue("message");
            header.createCell(7).setCellValue("created_at");

            // Данные
            for (UserMessagesLog log : logs) {
                Row row = sheet.createRow(rowIdx++);

                row.createCell(0).setCellValue(safeString(log.getId()));
                row.createCell(1).setCellValue(safeString(log.getTelegramId()));
                row.createCell(2).setCellValue(safeString(log.getUsername()));
                row.createCell(3).setCellValue(safeString(log.getFirstname()));
                row.createCell(4).setCellValue(safeString(log.getLastname()));
                row.createCell(5).setCellValue(safeString(log.getGoogleEmail()));
                row.createCell(6).setCellValue(safeMultiline(log.getMessage()));
                row.createCell(7).setCellValue(log.getCreatedAt() != null
                        ? log.getCreatedAt().format(DATE_TIME_FORMATTER)
                        : ""
                );
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw e;
        }
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safeMultiline(String value) {
        if (value == null) return "";
        return value.replace("\r", " ").replace("\n", " ");
    }
}
