package com.kuklin.manageapp.bots.caloriebot.models.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Серия дней с записями (еда или вода) для огонька в мини-приложении.
 *
 * @param current     текущая серия; сегодня ещё ничего нет — считается от вчера, серия не обрывается до конца дня
 * @param best        лучшая серия за всю историю (не меньше текущей)
 * @param todayLogged есть ли записи сегодня; false при current > 0 — серия «под угрозой»
 * @param last7       последние 7 дней по таймзоне пользователя, последний — сегодня
 */
public record StreakDto(int current, int best, boolean todayLogged, List<StreakDay> last7) {

    public record StreakDay(LocalDate date, boolean logged) {
    }
}
