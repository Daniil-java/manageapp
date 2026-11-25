package com.kuklin.manageapp.bots.caloriebot.report;

import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.dish.Micronutrient;
import com.kuklin.manageapp.bots.caloriebot.profile.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats.FiberStat;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats.MicronutrientStat;
import com.kuklin.manageapp.bots.caloriebot.report.dto.NutrientStats.Status;
import com.kuklin.manageapp.bots.caloriebot.report.dto.ReportSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Клетчатка и микронутриенты PDF-отчёта: средние в день против нормы — без ИИ и без обращений к БД.
 * <p>
 * Среднее — за дни, где у блюд есть эти данные: старые блюда и ручные записи их не имеют, микронутриенты
 * есть только у блюд, записанных с Premium. День с данными у части блюд немного занижает среднее — оценка и так примерная.
 */
public final class NutrientStatsCalculator {

    /** Меньше этой доли нормы — «мало» */
    private static final double LOW_SHARE = 0.7;
    /** Норма калорий, если в профиле её нет — для долей калорий (насыщенные жиры, сахар, ПНЖК) */
    private static final int DEFAULT_CALORIES_TARGET = 2000;

    private NutrientStatsCalculator() {
    }

    /** null — ни клетчатки, ни микронутриентов у блюд периода нет: блок в отчёте не показываем. */
    public static NutrientStats buildOrNull(ReportSource source) {
        Map<LocalDate, List<Dish>> byDate = groupByDate(source.dishes(), source.zoneId());
        UserNutritionProfile profile = source.profile();

        FiberStat fiber = buildFiberOrNull(byDate, profile);
        List<List<Dish>> microDays = byDate.values().stream()
                .filter(day -> day.stream().anyMatch(d -> d.getMicronutrients() != null && !d.getMicronutrients().isEmpty()))
                .toList();
        List<MicronutrientStat> micronutrients = buildMicronutrients(microDays, profile);

        if (fiber == null && micronutrients.isEmpty()) return null;
        return new NutrientStats(fiber, micronutrients, microDays.size(), byDate.size());
    }

    /** Норма клетчатки в день, г: 30 мужчинам, 25 женщинам (ВОЗ — не меньше 25), пол неизвестен — 28. */
    public static int getFiberTarget(UserNutritionProfile.Sex sex) {
        if (sex == UserNutritionProfile.Sex.MALE) return 30;
        if (sex == UserNutritionProfile.Sex.FEMALE) return 25;
        return 28;
    }

    private static FiberStat buildFiberOrNull(Map<LocalDate, List<Dish>> byDate, UserNutritionProfile profile) {
        int days = 0;
        long total = 0;
        for (List<Dish> day : byDate.values()) {
            if (day.stream().noneMatch(d -> d.getFiber() != null)) continue;
            days++;
            for (Dish d : day) total += d.getFiber() != null ? d.getFiber() : 0;
        }
        if (days == 0) return null;
        int avg = (int) Math.round((double) total / days);
        int target = getFiberTarget(profile.getSex());
        return new FiberStat(avg, target, days, avg < target * LOW_SHARE ? Status.LOW : Status.OK);
    }

    private static List<MicronutrientStat> buildMicronutrients(List<List<Dish>> days, UserNutritionProfile profile) {
        if (days.isEmpty()) return List.of();
        int calories = profile.getCaloriesNormPerDay() != null && profile.getCaloriesNormPerDay() > 0
                ? profile.getCaloriesNormPerDay() : DEFAULT_CALORIES_TARGET;

        List<MicronutrientStat> stats = new ArrayList<>();
        for (Micronutrient nutrient : Micronutrient.values()) {
            double total = 0;
            for (List<Dish> day : days) {
                for (Dish d : day) {
                    Double value = d.getMicronutrients() != null ? d.getMicronutrients().get(nutrient.getKey()) : null;
                    if (value != null) total += value;
                }
            }
            double avg = total / days.size();
            double norm = nutrient.getDailyNorm(profile.getSex(), profile.getAgeYears(), calories);
            stats.add(new MicronutrientStat(nutrient, avg, norm, buildStatus(nutrient, avg, norm)));
        }
        return stats;
    }

    private static Status buildStatus(Micronutrient nutrient, double avg, double norm) {
        if (nutrient.getLimit() == Micronutrient.Limit.MAX) return avg > norm ? Status.HIGH : Status.OK;
        return avg < norm * LOW_SHARE ? Status.LOW : Status.OK;
    }

    private static Map<LocalDate, List<Dish>> groupByDate(List<Dish> dishes, ZoneId zone) {
        Map<LocalDate, List<Dish>> byDate = new TreeMap<>();
        for (Dish d : dishes) {
            if (d.getCreated() == null) continue;
            byDate.computeIfAbsent(d.getCreated().atZone(zone).toLocalDate(), k -> new ArrayList<>()).add(d);
        }
        return byDate;
    }
}
