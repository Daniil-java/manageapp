package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Данные пользователя для PDF-отчёта, загруженные из БД одним заходом.
 *
 * @param startDate      первый день отчёта (в таймзоне пользователя)
 * @param endDate        последний день отчёта, обычно сегодня
 * @param previousDishes блюда за такой же период перед отчётным — для сравнения
 * @param targets        нормы, действовавшие в период
 * @param weights        взвешивания периода и за TREND_WINDOW_DAYS до него (тренд сглаживается и по ним),
 *                       плюс внесённые в период задним числом — их показывает дашборд
 */
public record ReportSource(ReportKind kind,
                           LocalDate startDate,
                           LocalDate endDate,
                           ZoneId zoneId,
                           List<Dish> dishes,
                           List<Dish> previousDishes,
                           List<UserNutritionProfileEntry> targets,
                           List<WeightEntry> weights,
                           List<WaterEntryDto> water,
                           UserNutritionProfile profile) {
}
