package com.kuklin.manageapp.bots.caloriebot.telegram.history;

import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.services.ReportTableService;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestFeatureUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final ReportTableService reportTableService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        try {
            ReportTableService.ReportResponse report =
                    reportTableService.getPeriodReport(
                            Instant.now().minus(30, ChronoUnit.DAYS),
                            Instant.now(),
                            telegramUser.getTelegramId()
                    );

            // 1. Текстовая таблица
            String tableText = report.table().printAll();

            // 2. HTML-файл (таблица + текст)
            String html = """
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <title>Отчёт</title>
                        <style>
                            body {
                                font-family: monospace;
                                white-space: pre;
                                padding: 16px;
                            }
                            .text {
                                margin-top: 24px;
                                white-space: normal;
                                font-family: Arial, sans-serif;
                            }
                        </style>
                    </head>
                    <body>
                    %s

                    <div class="text">
                    %s
                    </div>
                    </body>
                    </html>
                    """.formatted(
                    escapeHtml(tableText),
                    escapeHtml(report.text())
            );


            // 3. Временный файл
            Path tempFile = Files.createTempFile("report-", ".html");
            Files.writeString(tempFile, html, StandardCharsets.UTF_16);

            // 4. Отправка в Telegram
            SendDocument doc = SendDocument.builder()
                    .chatId(update.getMessage().getChatId())
                    .document(new InputFile(tempFile.toFile()))
                    .caption("📊 Отчёт за 30 дней")
                    .build();

            calorieTelegramBot.execute(doc);

            // 5. Удаляем файл
            Files.deleteIfExists(tempFile);
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    report.aiRequest()
            );

        } catch (Exception e) {
            log.error("REPORT SEND ERROR", e);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @Override
    public String getHandlerListName() {
        return "/testtempfeature";
    }
}
