package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.components.services.UtmReportService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Set;

/**
 * /utmstats — отчёт по источникам (UtmReportService). Только админы UTM-ссылок (calorie.utm.admin-telegram-ids),
 * остальным бот не отвечает.
 * <ul>
 *     <li>/utmstats — сводка за 30 дней;</li>
 *     <li>/utmstats 7 — сводка за 7 дней;</li>
 *     <li>/utmstats blogger_oleg [дни] — воронка одного источника; ref и zch — вся группа друзей / постов канала.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UtmStatsCalorieUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final UtmReportService utmReportService;

    @Value("${calorie.utm.admin-telegram-ids}")
    private Set<Long> adminTelegramIds;

    /** Что спросили: код источника (null — сводка) и период в днях. */
    record StatsRequest(String code, int days) {
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!adminTelegramIds.contains(telegramUser.getTelegramId())) {
            log.warn("utmstats by non-admin {}", telegramUser.getTelegramId());
            return;
        }
        StatsRequest request = parseRequest(update.getMessage().getText());
        String report = request.code() == null
                ? utmReportService.buildReport(request.days())
                : utmReportService.buildCodeReport(request.code(), request.days());
        calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), report);
    }

    /** Аргументы после команды в любом порядке: число — дни (1–365), остальное — код. */
    static StatsRequest parseRequest(String text) {
        String code = null;
        int days = UtmReportService.DEFAULT_DAYS;
        String[] parts = text.trim().split(TelegramBot.DEFAULT_DELIMETER);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.chars().allMatch(Character::isDigit) && part.length() <= 3) {
                days = Math.max(1, Math.min(UtmReportService.MAX_DAYS, Integer.parseInt(part)));
            } else {
                code = part;
            }
        }
        return new StatsRequest(code, days);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_UTM_STATS.getCommandText();
    }
}
