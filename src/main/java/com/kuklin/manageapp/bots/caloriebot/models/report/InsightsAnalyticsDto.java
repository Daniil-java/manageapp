package com.kuklin.manageapp.bots.caloriebot.models.report;

import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.DayStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.DayStatus;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.MacroStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.RhythmStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.WeightStat;

import java.time.LocalDate;
import java.util.List;

/**
 * Аналитика для вкладки Insights мини-приложения: календарь нормы, вес с трендом и прогнозом,
 * реальный расход, привычки. Считается тем же {@link com.kuklin.manageapp.bots.caloriebot.utils.ReportStatsCalculator},
 * что и PDF-отчёт. null в полях — данных не хватило, блок не показываем.
 *
 * @param calendar    все дни периода: калории, норма дня и насколько день в неё попал
 * @param weight      вес: взвешивания, тренд и прогноз; null — меньше двух взвешиваний за период
 * @param energy      реальный расход против формулы; null — мало записей или взвешиваний
 * @param habits      будни и выходные, окно питания, поздние калории, белок; null — нет записей еды
 */
public record InsightsAnalyticsDto(List<CalendarDay> calendar,
                                   Weight weight,
                                   Energy energy,
                                   Habits habits) {

    public static InsightsAnalyticsDto of(ReportStats stats) {
        return new InsightsAnalyticsDto(
                stats.days().stream().map(CalendarDay::of).toList(),
                Weight.ofOrNull(stats),
                Energy.ofOrNull(stats),
                Habits.ofOrNull(stats)
        );
    }

    /**
     * @param calories null — записей еды в этот день нет
     * @param target   норма калорий, действовавшая в этот день
     */
    public record CalendarDay(LocalDate date, Integer calories, Integer target, DayStatus status, boolean weekend) {
        static CalendarDay of(DayStat d) {
            return new CalendarDay(d.date(), d.calories(), d.target(), d.status(), d.weekend());
        }
    }

    /**
     * @param points      дни периода: взвешивание (null — не взвешивался) и сглаженный тренд
     * @param ratePerWeek темп, кг в неделю — наклон прямой по взвешиваниям
     * @param forecastKg  прогноз при текущем темпе на forecastDate; null — взвешивания слишком близко друг к другу
     */
    public record Weight(List<WeightPoint> points,
                         int measurements,
                         double trendStart,
                         double trendEnd,
                         double deltaKg,
                         double ratePerWeek,
                         Double forecastKg,
                         LocalDate forecastDate) {
        static Weight ofOrNull(ReportStats stats) {
            WeightStat w = stats.weight();
            if (w == null) return null;
            List<WeightPoint> points = stats.days().stream()
                    .filter(d -> d.trendWeight() != null)
                    .map(d -> new WeightPoint(d.date(), d.weight() != null ? d.weight().doubleValue() : null, round2(d.trendWeight())))
                    .toList();
            return new Weight(points, w.measurements(), round2(w.trendStart()), round2(w.trendEnd()), round2(w.deltaKg()),
                    round2(w.ratePerWeek()), w.forecastKg() != null ? round2(w.forecastKg()) : null, w.forecastDate());
        }
    }

    public record WeightPoint(LocalDate date, Double weight, double trend) {
    }

    /**
     * @param realTdee    реальный расход: средние калории − темп веса × 7 700
     * @param formulaTdee расход по формуле Миффлина × активность; null — профиль заполнен не полностью
     */
    public record Energy(int realTdee, Integer formulaTdee, int avgCalories, Integer avgTarget) {
        static Energy ofOrNull(ReportStats stats) {
            if (stats.realTdee() == null) return null;
            return new Energy(stats.realTdee(), stats.formulaTdee(), stats.avgCalories(), stats.avgTarget());
        }
    }

    /**
     * @param weekendSharePercent какую долю дефицита будней съедают выходные, %
     * @param firstMeal           среднее время первого приёма пищи, «09:40»
     * @param lateCaloriesPercent доля калорий с 21:00 до 05:00, %
     * @param proteinPerKg        белок на кг веса в день
     */
    public record Habits(int loggedDays,
                         int periodDays,
                         Integer weekdayAvgCalories,
                         Integer weekendAvgCalories,
                         Integer avgTarget,
                         Integer weekendSharePercent,
                         String firstMeal,
                         String lastMeal,
                         double mealsPerDay,
                         int lateCaloriesPercent,
                         Double proteinPerKg,
                         Integer weekdayAvgProteins,
                         Integer weekendAvgProteins) {
        static Habits ofOrNull(ReportStats stats) {
            RhythmStat r = stats.rhythm();
            if (r == null) return null;
            MacroStat m = stats.macros();
            return new Habits(
                    stats.loggedDays(),
                    stats.days().size(),
                    stats.weekdayAvgCalories(),
                    stats.weekendAvgCalories(),
                    stats.avgTarget(),
                    stats.weekendShare() != null ? (int) Math.round(stats.weekendShare() * 100) : null,
                    formatMinutes(r.firstMealMinutes()),
                    formatMinutes(r.lastMealMinutes()),
                    Math.round(r.mealsPerDay() * 10) / 10.0,
                    (int) Math.round(r.lateShare() * 100),
                    m != null && m.proteinPerKg() != null ? Math.round(m.proteinPerKg() * 10) / 10.0 : null,
                    m != null ? m.weekdayAvgProteins() : null,
                    m != null ? m.weekendAvgProteins() : null
            );
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static String formatMinutes(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
