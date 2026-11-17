package com.kuklin.manageapp.bots.caloriebot.report;

import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.weight.WeightEntryService;
import com.kuklin.manageapp.bots.caloriebot.water.WaterEntryService;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfileEntryService;
import com.kuklin.manageapp.bots.caloriebot.dish.DishService;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Загружает из БД всё для расчёта метрик ({@link com.kuklin.manageapp.bots.caloriebot.report.ReportStatsCalculator}):
 * PDF-отчёт и аналитика вкладки Insights считаются по одним и тем же данным.
 */
@Service
@RequiredArgsConstructor
public class ReportSourceLoader {

    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserNutritionProfileEntryService userNutritionProfileEntryService;
    private final UserSettingsService userSettingsService;
    private final WeightEntryService weightEntryService;
    private final WaterEntryService waterEntryService;

    /** Последние kind.getDays() дней по таймзоне пользователя, включая сегодняшний (до момента now). */
    public ReportSource loadLastDays(ReportKind kind, Instant now, Long userId) {
        ZoneId zone = userSettingsService.getZoneId(userId);
        LocalDate endDate = LocalDate.ofInstant(now, zone);
        return load(kind, endDate.minusDays(kind.getDays() - 1), endDate, now, zone, userId);
    }

    /** Дни from–to включительно по таймзоне пользователя. */
    public ReportSource loadPeriod(LocalDate from, LocalDate to, Long userId) {
        ZoneId zone = userSettingsService.getZoneId(userId);
        long days = to.toEpochDay() - from.toEpochDay() + 1;
        ReportKind kind = days >= ReportKind.MONTH.getDays() ? ReportKind.MONTH : ReportKind.WEEK;
        Instant upperBound = to.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
        return load(kind, from, to, upperBound, zone, userId);
    }

    private ReportSource load(ReportKind kind, LocalDate startDate, LocalDate endDate, Instant upperBound, ZoneId zone, Long userId) {
        long days = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        Instant from = startDate.atStartOfDay(zone).toInstant();
        Instant previousFrom = startDate.minusDays(days).atStartOfDay(zone).toInstant();

        return new ReportSource(
                kind,
                startDate,
                endDate,
                zone,
                dishService.getAllDishedByUserIdAndPeriod(userId, from, upperBound),
                dishService.getAllDishedByUserIdAndPeriod(userId, previousFrom, from.minusMillis(1)),
                // Норма дня — та, что действовала в его начало
                userNutritionProfileEntryService.getAllActiveBetween(userId, from, endDate.atStartOfDay(zone).toInstant()),
                // Тренд в начале периода сглаживается и по взвешиваниям до него; дашборд берёт внесённые за период
                weightEntryService.getReportWeights(userId, startDate.minusDays(ReportStatsCalculator.TREND_WINDOW_DAYS),
                        endDate, from, upperBound),
                waterEntryService.getAllWaterEntryByPeriod(userId, startDate, endDate),
                userNutritionProfileService.getOrCreateProfile(userId)
        );
    }
}
