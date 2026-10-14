package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Всё, что посчитано для PDF-отчёта кодом, без ИИ. ИИ получает эти цифры готовыми и только объясняет их.
 * null в полях — данных не хватило, блок в отчёте не показываем.
 *
 * @param days            все дни периода по порядку, в том числе без записей
 * @param avgCalories     средние калории за дни с записями
 * @param prevAvgCalories то же за предыдущий такой же период
 * @param inCorridorDays  дней в коридоре нормы ±10%
 * @param streakDays      дней с записями подряд к концу периода
 * @param weekendShare    какую долю дефицита будней съедает «лишнее» выходных (0.39 = 39%)
 * @param realTdee        реальный расход: средние калории − темп веса за день × 7 700
 * @param formulaTdee     расход по формуле Миффлина × активность
 * @param targetChange    день, с которого действует новая норма калорий (подпись на графике)
 */
public record ReportStats(ReportKind kind,
                          LocalDate startDate,
                          LocalDate endDate,
                          List<DayStat> days,
                          Integer avgCalories,
                          Integer prevAvgCalories,
                          int loggedDays,
                          int inCorridorDays,
                          int streakDays,
                          Integer avgTarget,
                          Integer weekdayAvgCalories,
                          Integer weekendAvgCalories,
                          Double weekendShare,
                          WeightStat weight,
                          Integer realTdee,
                          Integer formulaTdee,
                          MacroStat macros,
                          RhythmStat rhythm,
                          List<DishCount> topByCount,
                          List<DishCount> topByCalories,
                          List<CategoryShare> categories,
                          WaterStat water,
                          Integer avgAiConfidence,
                          DayStat targetChange) {

    public boolean hasFood() {
        return loggedDays > 0;
    }

    /** Насколько день совпал с нормой калорий */
    public enum DayStatus {
        /** Записей нет */
        NONE,
        /** Меньше нормы больше чем на 10% */
        UNDER,
        /** В коридоре ±10% */
        OK,
        /** Больше нормы на 10–25% */
        OVER,
        /** Больше нормы больше чем на 25% */
        BIG_OVER
    }

    /**
     * Один день периода.
     *
     * @param calories    null — записей еды нет
     * @param target      норма калорий, действовавшая в этот день (null — нормы нет)
     * @param weight      взвешивание в этот день
     * @param trendWeight сглаженный вес (тренд без скачков воды); null — взвешиваний ещё не было
     */
    public record DayStat(LocalDate date,
                          boolean weekend,
                          Integer calories,
                          Integer target,
                          DayStatus status,
                          int proteins,
                          int fats,
                          int carbs,
                          Integer waterMl,
                          BigDecimal weight,
                          Double trendWeight) {
        public boolean logged() {
            return calories != null;
        }
    }

    /**
     * Вес за период.
     *
     * @param trendStart  тренд в первый день периода, где он есть
     * @param trendEnd    тренд в последний день периода
     * @param ratePerWeek темп за неделю, кг — наклон прямой по взвешиваниям периода
     * @param forecastKg  прогноз при текущем темпе на forecastDate; null — данных мало
     */
    public record WeightStat(int measurements,
                             double trendStart,
                             double trendEnd,
                             double deltaKg,
                             double ratePerWeek,
                             Double forecastKg,
                             LocalDate forecastDate) {
    }

    /** Средние БЖУ за дни с записями и цели (по последней норме) */
    public record MacroStat(int avgProteins,
                            int avgFats,
                            int avgCarbs,
                            Integer targetProteins,
                            Integer targetFats,
                            Integer targetCarbs,
                            Double proteinPerKg,
                            Integer weekdayAvgProteins,
                            Integer weekendAvgProteins) {
    }

    /**
     * Ритм питания.
     *
     * @param hourlyCalories калории за период по часам суток, 24 значения
     * @param firstMealMinutes среднее время первого приёма пищи, минуты от полуночи
     * @param lateShare доля калорий с 21:00 до 05:00
     */
    public record RhythmStat(List<Integer> hourlyCalories,
                             int firstMealMinutes,
                             int lastMealMinutes,
                             double mealsPerDay,
                             double lateShare) {
    }

    public record DishCount(String emoji, String name, int count, int calories) {
    }

    public record CategoryShare(Dish.FoodCategory category, int calories, double share) {
    }

    /** Вода за дни с записями воды или еды */
    public record WaterStat(int avgMl, int targetMl, int daysMet, int daysCounted) {
    }
}
