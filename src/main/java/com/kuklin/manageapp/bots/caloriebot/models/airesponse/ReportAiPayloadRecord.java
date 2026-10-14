package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Данные для ИИ в PDF-отчёте: посчитанные метрики вместо сырых блюд.
 * ИИ не пересчитывает цифры, а объясняет их — так дешевле и без выдуманных чисел.
 */
public record ReportAiPayloadRecord(InsightPayloadRecord.InsightProfile profile,
                                    Summary summary,
                                    List<TopDish> topByCount,
                                    List<TopDish> topByCalories,
                                    List<String> lateDishes,
                                    Map<String, Integer> categoryPercents) {

    /** Сколько поздних блюд показать ИИ */
    private static final int LATE_DISHES_LIMIT = 5;

    public static ReportAiPayloadRecord of(ReportStats stats, UserNutritionProfile profile, List<Dish> dishes, ZoneId zoneId) {
        Map<String, Integer> categories = new LinkedHashMap<>();
        stats.categories().forEach(c -> categories.put(c.category().name(), (int) Math.round(c.share() * 100)));

        return new ReportAiPayloadRecord(
                InsightPayloadRecord.InsightProfile.of(profile),
                Summary.of(stats),
                stats.topByCount().stream().map(TopDish::of).toList(),
                stats.topByCalories().stream().map(TopDish::of).toList(),
                findLateDishes(dishes, zoneId),
                categories
        );
    }

    /** Самые частые блюда с 21:00 до 05:00 */
    private static List<String> findLateDishes(List<Dish> dishes, ZoneId zoneId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Dish d : dishes) {
            if (d.getCreated() == null || d.getName() == null) continue;
            int hour = LocalTime.from(d.getCreated().atZone(zoneId)).getHour();
            if (hour >= 21 || hour < 5) counts.merge(d.getName().trim(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(LATE_DISHES_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    public record TopDish(String name, int count, int calories) {
        static TopDish of(ReportStats.DishCount d) {
            return new TopDish(d.name(), d.count(), d.calories());
        }
    }

    /** Главные цифры периода; null — данных не хватило */
    public record Summary(int periodDays,
                          int loggedDays,
                          Integer avgCalories,
                          Integer prevPeriodAvgCalories,
                          Integer avgCaloriesTarget,
                          int daysWithinTarget10Percent,
                          Integer weekdayAvgCalories,
                          Integer weekendAvgCalories,
                          Integer weekendEatsWeekdayDeficitPercent,
                          Double weightTrendChangeKg,
                          Double weightPacePerWeekKg,
                          Double weightForecastKg,
                          Integer realExpenditureKcal,
                          Integer formulaExpenditureKcal,
                          Integer avgProteins,
                          Integer avgFats,
                          Integer avgCarbs,
                          Double proteinPerKg,
                          Integer weekdayAvgProteins,
                          Integer weekendAvgProteins,
                          String avgFirstMeal,
                          String avgLastMeal,
                          Double mealsPerDay,
                          Integer lateCaloriesPercent,
                          Integer avgWaterMl,
                          Integer waterTargetMl,
                          Integer waterTargetMetDays) {

        static Summary of(ReportStats s) {
            var weight = s.weight();
            var macros = s.macros();
            var rhythm = s.rhythm();
            var water = s.water();
            return new Summary(
                    s.days().size(),
                    s.loggedDays(),
                    s.avgCalories(),
                    s.prevAvgCalories(),
                    s.avgTarget(),
                    s.inCorridorDays(),
                    s.weekdayAvgCalories(),
                    s.weekendAvgCalories(),
                    s.weekendShare() != null ? (int) Math.round(s.weekendShare() * 100) : null,
                    weight != null ? round1(weight.deltaKg()) : null,
                    weight != null ? round1(weight.ratePerWeek()) : null,
                    weight != null && weight.forecastKg() != null ? round1(weight.forecastKg()) : null,
                    s.realTdee(),
                    s.formulaTdee(),
                    macros != null ? macros.avgProteins() : null,
                    macros != null ? macros.avgFats() : null,
                    macros != null ? macros.avgCarbs() : null,
                    macros != null && macros.proteinPerKg() != null ? round1(macros.proteinPerKg()) : null,
                    macros != null ? macros.weekdayAvgProteins() : null,
                    macros != null ? macros.weekendAvgProteins() : null,
                    rhythm != null ? formatMinutes(rhythm.firstMealMinutes()) : null,
                    rhythm != null ? formatMinutes(rhythm.lastMealMinutes()) : null,
                    rhythm != null ? round1(rhythm.mealsPerDay()) : null,
                    rhythm != null ? (int) Math.round(rhythm.lateShare() * 100) : null,
                    water != null ? water.avgMl() : null,
                    water != null ? water.targetMl() : null,
                    water != null ? water.daysMet() : null
            );
        }

        private static double round1(double value) {
            return Math.round(value * 10) / 10.0;
        }

        private static String formatMinutes(int minutes) {
            return String.format("%02d:%02d", minutes / 60, minutes % 60);
        }
    }
}
