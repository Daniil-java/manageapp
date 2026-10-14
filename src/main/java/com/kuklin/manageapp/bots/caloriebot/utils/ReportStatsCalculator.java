package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import com.kuklin.manageapp.bots.caloriebot.entities.WeightEntry;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.WaterEntryDto;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportSource;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Считает метрики PDF-отчёта из записей пользователя — без ИИ и без обращений к БД.
 */
public final class ReportStatsCalculator {

    /** Коридор нормы: ±10% — день «в норме» */
    private static final long CORRIDOR_PERCENT = 10;
    /** Больше нормы на 25% — сильный перебор */
    private static final long BIG_OVER_PERCENT = 25;
    /** Сглаживание веса: прямая по взвешиваниям в окне ±N дней вокруг дня */
    private static final int TREND_WINDOW_DAYS = 7;
    private static final int MIN_TREND_SPREAD_DAYS = 4;
    /** Калорий в килограмме жира — для реального расхода */
    private static final int KCAL_PER_KG = 7700;
    /** Блюда с перерывом меньше этого — один приём пищи */
    private static final int MEAL_GAP_MINUTES = 45;
    /** Поздние калории: с 21:00 до 05:00 */
    private static final int LATE_FROM_HOUR = 21;
    private static final int LATE_TO_HOUR = 5;
    private static final int TOP_LIMIT = 6;
    /** Меньше дней с записями — среднее за прошлый период не сравниваем */
    private static final int MIN_DAYS_FOR_AVG = 3;
    /** Реальный расход: минимум дней с записями и дней между первым и последним днём тренда */
    private static final int MIN_DAYS_FOR_TDEE = 7;
    private static final int MIN_WEIGHT_SPAN_DAYS = 7;
    /** Прогноз веса: минимум дней тренда */
    private static final int MIN_FORECAST_SPAN_DAYS = 5;
    /** Темп веса больше этого — скачки данных, а не тренд; прогноз ограничиваем */
    private static final double MAX_RATE_KG_PER_WEEK = 1.5;
    /** Реальный расход вне этих границ — значит, записана не вся еда: не показываем */
    private static final int MIN_SANE_TDEE = 1000;
    private static final int MAX_SANE_TDEE = 5000;

    private ReportStatsCalculator() {
    }

    public static ReportStats buildStats(ReportSource source) {
        ZoneId zone = source.zoneId();
        Map<LocalDate, List<Dish>> dishesByDate = groupByDate(source.dishes(), zone);
        List<DayStat> days = buildDays(source, dishesByDate);
        List<DayStat> logged = days.stream().filter(DayStat::logged).toList();

        Integer avgCalories = averageOrNull(logged, DayStat::calories);
        Integer prevAvgCalories = buildPrevAvgCaloriesOrNull(source.previousDishes(), zone);
        int inCorridor = (int) logged.stream().filter(d -> d.status() == DayStatus.OK).count();
        Integer avgTarget = averageOrNull(logged.stream().filter(d -> d.target() != null).toList(), DayStat::target);
        Integer weekdayAvg = averageOrNull(logged.stream().filter(d -> !d.weekend()).toList(), DayStat::calories);
        Integer weekendAvg = averageOrNull(logged.stream().filter(DayStat::weekend).toList(), DayStat::calories);

        WeightStat weight = buildWeightStatOrNull(source, days);
        Integer realTdee = buildRealTdeeOrNull(weight, days, avgCalories, logged.size());
        Integer reference = realTdee != null ? realTdee : avgTarget;

        return new ReportStats(
                source.kind(),
                source.startDate(),
                source.endDate(),
                days,
                avgCalories,
                prevAvgCalories,
                logged.size(),
                inCorridor,
                buildStreak(days),
                avgTarget,
                weekdayAvg,
                weekendAvg,
                buildWeekendShareOrNull(weekdayAvg, weekendAvg, reference, source.profile().getGoal()),
                weight,
                realTdee,
                UserNutritionProfileService.calcFormulaMaintenanceCaloriesOrNull(source.profile()),
                buildMacrosOrNull(source, logged, weight),
                buildRhythmOrNull(dishesByDate, source, logged.size()),
                buildTop(source.dishes(), Comparator.comparingInt(DishCount::count).reversed()
                        .thenComparing(Comparator.comparingInt(DishCount::calories).reversed())),
                buildTop(source.dishes(), Comparator.comparingInt(DishCount::calories).reversed()),
                buildCategories(source.dishes()),
                buildWaterOrNull(source, days),
                buildAvgAiConfidenceOrNull(source.dishes()),
                findTargetChangeOrNull(days)
        );
    }

    // ── Дни ─────────────────────────────────────────────────────────

    private static List<DayStat> buildDays(ReportSource source, Map<LocalDate, List<Dish>> dishesByDate) {
        Map<LocalDate, Integer> waterByDate = source.water().stream()
                .filter(w -> w.getEntryDate() != null && w.getAmount() != null)
                .collect(Collectors.groupingBy(WaterEntryDto::getEntryDate, Collectors.summingInt(WaterEntryDto::getAmount)));
        TreeMap<LocalDate, BigDecimal> weightByDate = new TreeMap<>();
        for (WeightEntry w : source.weights()) {
            if (w.getEntryDate() != null && w.getWeight() != null) weightByDate.put(w.getEntryDate(), w.getWeight());
        }

        Double trend = null;

        List<DayStat> days = new ArrayList<>();
        for (LocalDate date = source.startDate(); !date.isAfter(source.endDate()); date = date.plusDays(1)) {
            List<Dish> dayDishes = dishesByDate.getOrDefault(date, List.of());
            Integer calories = dayDishes.isEmpty() ? null : sum(dayDishes, Dish::getCalories);
            Integer target = findCaloriesTargetOrNull(source, date);

            BigDecimal weight = weightByDate.get(date);
            Double local = buildLocalTrendOrNull(weightByDate, date, source.endDate());
            // Взвешиваний рядом нет — тренд держим на последнем значении
            if (local != null) trend = local;

            days.add(new DayStat(
                    date,
                    isWeekend(date),
                    calories,
                    target,
                    buildStatus(calories, target),
                    sum(dayDishes, Dish::getProteins),
                    sum(dayDishes, Dish::getFats),
                    sum(dayDishes, Dish::getCarbohydrates),
                    waterByDate.get(date),
                    weight,
                    trend
            ));
        }
        return days;
    }

    /**
     * Сглаженный вес на дату: взвешенная прямая по взвешиваниям в окне ±{@value #TREND_WINDOW_DAYS} дней
     * (ближние весят больше). В отличие от скользящего среднего, не запаздывает на краях периода.
     * null — в окне нет взвешиваний.
     */
    static Double buildLocalTrendOrNull(NavigableMap<LocalDate, BigDecimal> weightByDate, LocalDate date, LocalDate endDate) {
        LocalDate to = date.plusDays(TREND_WINDOW_DAYS).isAfter(endDate) ? endDate : date.plusDays(TREND_WINDOW_DAYS);
        var window = weightByDate.subMap(date.minusDays(TREND_WINDOW_DAYS), true, to, true);
        if (window.isEmpty()) return null;

        double sumW = 0, sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (Map.Entry<LocalDate, BigDecimal> e : window.entrySet()) {
            double x = ChronoUnit.DAYS.between(date, e.getKey());
            double d = Math.abs(x) / (TREND_WINDOW_DAYS + 1);
            double w = Math.pow(1 - d * d * d, 3); // tricube: вес падает к краям окна
            double y = e.getValue().doubleValue();
            sumW += w;
            sumX += w * x;
            sumY += w * y;
            sumXX += w * x * x;
            sumXY += w * x * y;
        }
        double meanX = sumX / sumW, meanY = sumY / sumW;
        double varX = sumXX / sumW - meanX * meanX;
        // Прямую строим только по трём и больше взвешиваниям, разнесённым хотя бы на MIN_TREND_SPREAD_DAYS:
        // по двум близким замерам она уводит тренд далеко за пределы данных
        long spread = ChronoUnit.DAYS.between(window.firstKey(), window.lastKey());
        if (window.size() < 3 || spread < MIN_TREND_SPREAD_DAYS || varX < 1e-9) return meanY;
        double slope = (sumXY / sumW - meanX * meanY) / varX;
        // Значение прямой в точке date (x = 0)
        return meanY - slope * meanX;
    }

    static DayStatus buildStatus(Integer calories, Integer target) {
        if (calories == null) return DayStatus.NONE;
        if (target == null || target <= 0) return DayStatus.OK;
        // В процентах целыми числами: 2 200 при норме 2 000 — ровно +10%, без ошибки округления double
        long diffPercent100 = (long) (calories - target) * 100;
        if (diffPercent100 < -CORRIDOR_PERCENT * target) return DayStatus.UNDER;
        if (diffPercent100 <= CORRIDOR_PERCENT * target) return DayStatus.OK;
        if (diffPercent100 <= BIG_OVER_PERCENT * target) return DayStatus.OVER;
        return DayStatus.BIG_OVER;
    }

    /** Норма калорий, действовавшая в начале дня; истории норм нет — норма из профиля. */
    private static Integer findCaloriesTargetOrNull(ReportSource source, LocalDate date) {
        UserNutritionProfileEntry entry = findTargetEntryOrNull(source, date);
        return entry != null ? entry.getCaloriesNormPerDay() : source.profile().getCaloriesNormPerDay();
    }

    private static UserNutritionProfileEntry findTargetEntryOrNull(ReportSource source, LocalDate date) {
        var instant = date.atStartOfDay(source.zoneId()).toInstant();
        for (UserNutritionProfileEntry e : source.targets()) {
            boolean afterFrom = e.getValidFrom() == null || !instant.isBefore(e.getValidFrom());
            boolean beforeTo = e.getValidTo() == null || instant.isBefore(e.getValidTo());
            if (afterFrom && beforeTo) return e;
        }
        return null;
    }

    private static int buildStreak(List<DayStat> days) {
        int i = days.size() - 1;
        // Сегодня ещё не закончился: пустой последний день серию не обрывает
        if (i >= 0 && !days.get(i).logged()) i--;
        int streak = 0;
        while (i >= 0 && days.get(i).logged()) {
            streak++;
            i--;
        }
        return streak;
    }

    private static DayStat findTargetChangeOrNull(List<DayStat> days) {
        DayStat change = null;
        for (int i = 1; i < days.size(); i++) {
            Integer prev = days.get(i - 1).target();
            Integer cur = days.get(i).target();
            if (prev != null && cur != null && !prev.equals(cur)) change = days.get(i);
        }
        return change;
    }

    private static Integer buildPrevAvgCaloriesOrNull(List<Dish> previousDishes, ZoneId zone) {
        Map<LocalDate, List<Dish>> byDate = groupByDate(previousDishes, zone);
        if (byDate.size() < MIN_DAYS_FOR_AVG) return null;
        return (int) Math.round(byDate.values().stream().mapToInt(d -> sum(d, Dish::getCalories)).average().orElse(0));
    }

    private static Double buildWeekendShareOrNull(Integer weekdayAvg, Integer weekendAvg, Integer reference,
                                                  UserNutritionProfile.Goal goal) {
        // При наборе веса недобор будней — не «дефицит», который жалко съесть, а проблема сама по себе
        if (goal == UserNutritionProfile.Goal.GAIN_WEIGHT) return null;
        if (weekdayAvg == null || weekendAvg == null || reference == null) return null;
        if (weekendAvg <= weekdayAvg || reference <= weekdayAvg) return null;
        // «Лишнее» двух выходных против дефицита пяти будней
        return (weekendAvg - weekdayAvg) * 2.0 / ((reference - weekdayAvg) * 5.0);
    }

    // ── Вес и расход ────────────────────────────────────────────────

    private static WeightStat buildWeightStatOrNull(ReportSource source, List<DayStat> days) {
        int measurements = (int) days.stream().filter(d -> d.weight() != null).count();
        DayStat first = days.stream().filter(d -> d.trendWeight() != null).findFirst().orElse(null);
        if (measurements < 2 || first == null) return null;

        double trendStart = first.trendWeight();
        double trendEnd = days.get(days.size() - 1).trendWeight();
        List<DayStat> measured = days.stream().filter(d -> d.weight() != null).toList();
        long span = ChronoUnit.DAYS.between(measured.get(0).date(), measured.get(measured.size() - 1).date());
        // Темп — наклон прямой по взвешиваниям: сглаженный тренд запаздывает и занижал бы темп
        double ratePerDay = buildSlopePerDay(measured);
        double maxPerDay = MAX_RATE_KG_PER_WEEK / 7;
        ratePerDay = Math.max(-maxPerDay, Math.min(maxPerDay, ratePerDay));

        int forecastDays = source.kind().getForecastDays();
        Double forecast = span >= MIN_FORECAST_SPAN_DAYS ? trendEnd + ratePerDay * forecastDays : null;
        return new WeightStat(measurements, trendStart, trendEnd, trendEnd - trendStart, ratePerDay * 7,
                forecast, source.endDate().plusDays(forecastDays));
    }

    /** Наклон прямой (метод наименьших квадратов) по взвешиваниям, кг в день */
    private static double buildSlopePerDay(List<DayStat> measured) {
        LocalDate origin = measured.get(0).date();
        double n = measured.size(), sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (DayStat d : measured) {
            double x = ChronoUnit.DAYS.between(origin, d.date());
            double y = d.weight().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        return denominator != 0 ? (n * sumXY - sumX * sumY) / denominator : 0;
    }

    private static Integer buildRealTdeeOrNull(WeightStat weight, List<DayStat> days, Integer avgCalories, int loggedDays) {
        if (weight == null || avgCalories == null || loggedDays < MIN_DAYS_FOR_TDEE) return null;
        List<DayStat> measured = days.stream().filter(d -> d.weight() != null).toList();
        long span = ChronoUnit.DAYS.between(measured.get(0).date(), measured.get(measured.size() - 1).date());
        if (span < MIN_WEIGHT_SPAN_DAYS) return null;

        double tdee = avgCalories - weight.ratePerWeek() / 7 * KCAL_PER_KG;
        if (tdee < MIN_SANE_TDEE || tdee > MAX_SANE_TDEE) return null;
        return (int) Math.round(tdee / 10) * 10;
    }

    // ── Состав и ритм ───────────────────────────────────────────────

    private static MacroStat buildMacrosOrNull(ReportSource source, List<DayStat> logged, WeightStat weight) {
        if (logged.isEmpty()) return null;
        UserNutritionProfileEntry entry = findTargetEntryOrNull(source, source.endDate());
        UserNutritionProfile profile = source.profile();
        int avgProteins = averageOrNull(logged, DayStat::proteins);

        Double bodyWeight = null;
        if (weight != null) {
            bodyWeight = weight.trendEnd();
        } else if (profile.getCurrentWeightKg() != null) {
            bodyWeight = profile.getCurrentWeightKg().doubleValue();
        }
        return new MacroStat(
                avgProteins,
                averageOrNull(logged, DayStat::fats),
                averageOrNull(logged, DayStat::carbs),
                entry != null ? entry.getProteinsNormGramsPerDay() : profile.getProteinsNormGramsPerDay(),
                entry != null ? entry.getFatsNormGramsPerDay() : profile.getFatsNormGramsPerDay(),
                entry != null ? entry.getCarbsNormGramsPerDay() : profile.getCarbsNormGramsPerDay(),
                bodyWeight != null && bodyWeight > 0 ? avgProteins / bodyWeight : null,
                averageOrNull(logged.stream().filter(d -> !d.weekend()).toList(), DayStat::proteins),
                averageOrNull(logged.stream().filter(DayStat::weekend).toList(), DayStat::proteins)
        );
    }

    private static RhythmStat buildRhythmOrNull(Map<LocalDate, List<Dish>> dishesByDate, ReportSource source, int loggedDays) {
        if (loggedDays == 0) return null;
        ZoneId zone = source.zoneId();
        int[] hourly = new int[24];
        long firstSum = 0, lastSum = 0, total = 0, late = 0;
        int meals = 0, days = 0;

        for (Map.Entry<LocalDate, List<Dish>> e : dishesByDate.entrySet()) {
            if (e.getKey().isBefore(source.startDate()) || e.getKey().isAfter(source.endDate())) continue;
            List<Integer> minutes = new ArrayList<>();
            for (Dish d : e.getValue()) {
                LocalTime time = d.getCreated().atZone(zone).toLocalTime();
                int cal = nvl(d.getCalories());
                hourly[time.getHour()] += cal;
                total += cal;
                if (time.getHour() >= LATE_FROM_HOUR || time.getHour() < LATE_TO_HOUR) late += cal;
                minutes.add(time.getHour() * 60 + time.getMinute());
            }
            Collections.sort(minutes);
            firstSum += minutes.get(0);
            lastSum += minutes.get(minutes.size() - 1);
            meals++;
            for (int i = 1; i < minutes.size(); i++) {
                if (minutes.get(i) - minutes.get(i - 1) > MEAL_GAP_MINUTES) meals++;
            }
            days++;
        }
        if (days == 0) return null;
        return new RhythmStat(
                Arrays.stream(hourly).boxed().toList(),
                (int) (firstSum / days),
                (int) (lastSum / days),
                (double) meals / days,
                total > 0 ? (double) late / total : 0
        );
    }

    /** Топ блюд: одинаковые названия без учёта регистра и пробелов — одно блюдо. */
    private static List<DishCount> buildTop(List<Dish> dishes, Comparator<DishCount> order) {
        Map<String, List<Dish>> byName = new LinkedHashMap<>();
        for (Dish d : dishes) {
            if (d.getName() == null || d.getName().isBlank()) continue;
            byName.computeIfAbsent(d.getName().trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(d);
        }
        return byName.values().stream()
                .map(group -> {
                    Dish latest = group.stream().max(Comparator.comparing(Dish::getCreated, Comparator.nullsFirst(Comparator.naturalOrder()))).orElseThrow();
                    String emoji = group.stream().map(Dish::getEmojiIcon).filter(Objects::nonNull).findFirst().orElse(null);
                    return new DishCount(emoji, latest.getName().trim(), group.size(), sum(group, Dish::getCalories));
                })
                .sorted(order)
                .limit(TOP_LIMIT)
                .toList();
    }

    private static List<CategoryShare> buildCategories(List<Dish> dishes) {
        Map<Dish.FoodCategory, Integer> byCategory = new EnumMap<>(Dish.FoodCategory.class);
        for (Dish d : dishes) {
            Dish.FoodCategory category = d.getCategory() != null ? d.getCategory() : Dish.FoodCategory.UNKNOWN;
            byCategory.merge(category, nvl(d.getCalories()), Integer::sum);
        }
        int total = byCategory.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return List.of();
        return byCategory.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new CategoryShare(e.getKey(), e.getValue(), (double) e.getValue() / total))
                .sorted(Comparator.comparingInt(CategoryShare::calories).reversed())
                .toList();
    }

    private static WaterStat buildWaterOrNull(ReportSource source, List<DayStat> days) {
        List<DayStat> withWater = days.stream().filter(d -> d.waterMl() != null).toList();
        if (withWater.isEmpty()) return null;
        Integer profileTarget = source.profile().getWaterTargetMlPerDay();
        int target = profileTarget != null && profileTarget > 0 ? profileTarget : UserNutritionProfile.DEF_WATER_ML;
        return new WaterStat(
                averageOrNull(withWater, DayStat::waterMl),
                target,
                (int) withWater.stream().filter(d -> d.waterMl() >= target).count(),
                withWater.size()
        );
    }

    private static Integer buildAvgAiConfidenceOrNull(List<Dish> dishes) {
        var confidence = dishes.stream().map(Dish::getAiConfidence).filter(Objects::nonNull).mapToInt(Integer::intValue).summaryStatistics();
        return confidence.getCount() > 0 ? (int) Math.round(confidence.getAverage()) : null;
    }

    // ── Утилиты ─────────────────────────────────────────────────────

    private static Map<LocalDate, List<Dish>> groupByDate(List<Dish> dishes, ZoneId zone) {
        return dishes.stream()
                .filter(d -> d.getCreated() != null)
                .collect(Collectors.groupingBy(d -> d.getCreated().atZone(zone).toLocalDate(), TreeMap::new, Collectors.toList()));
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static <T> Integer averageOrNull(List<T> items, ToIntFunction<T> value) {
        if (items.isEmpty()) return null;
        return (int) Math.round(items.stream().mapToInt(value).average().orElse(0));
    }

    /** Сумма поля блюд; пустое поле — 0 */
    private static int sum(List<Dish> dishes, Function<Dish, Integer> value) {
        return dishes.stream().mapToInt(d -> nvl(value.apply(d))).sum();
    }

    private static int nvl(Integer v) {
        return v != null ? v : 0;
    }
}
