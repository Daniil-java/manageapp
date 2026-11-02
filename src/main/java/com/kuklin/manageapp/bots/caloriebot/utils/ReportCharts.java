package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.DayStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.MacroStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.RhythmStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.WaterStat;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.WeightStat;
import com.kuklin.manageapp.bots.caloriebot.texts.PdfTexts;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

import static com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys.*;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportHtml.escapeHtml;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportPalette.*;

/**
 * Графики PDF-отчёта — статический SVG: openhtmltopdf рисует его через Batik, JS в PDF нет.
 * Ширина графиков — под карточку на всю ширину A4 с полями отчёта.
 * Жирные подписи — семейством «Nunito ExtraBold» из @font-face шаблона: font-weight Batik не применяет.
 */
public final class ReportCharts {
    private ReportCharts() {
    }

    /** Ширина графика в карточке на всю ширину страницы, px */
    private static final int WIDTH = 650;
    /** Месячный график подписываем по понедельникам, недельный — каждый день */
    private static final int DAILY_LABELS_MAX_DAYS = 10;

    // ── Калории и норма ─────────────────────────────────────────────

    public static String buildCaloriesChart(ReportStats stats, PdfTexts texts) {
        List<DayStat> days = stats.days();
        int n = days.size();
        int h = 230, pl = 40, pr = 8, pt = 18, pb = 26;
        double pw = WIDTH - pl - pr, ph = h - pt - pb, slot = pw / n, bw = slot * 0.62;

        int maxValue = days.stream()
                .mapToInt(d -> Math.max(d.calories() != null ? d.calories() : 0, d.target() != null ? (int) (d.target() * 1.1) : 0))
                .max().orElse(0);
        int step = maxValue > 2400 ? 1000 : 500;
        int yMax = Math.max(step * 2, (int) Math.ceil(maxValue * 1.08 / step) * step);
        DoubleUnaryOperator y = v -> pt + ph - v / yMax * ph;

        StringBuilder s = openSvg(h);
        for (int i = 0; i < n; i++) {
            if (days.get(i).weekend()) s.append(rect(pl + i * slot, pt, slot, ph, BG_SOFT, 0));
        }
        for (int v = 0; v <= yMax; v += step) {
            s.append(line(pl, y.applyAsDouble(v), WIDTH - pr, y.applyAsDouble(v), BORDER, 1, null));
            s.append(text(pl - 6, y.applyAsDouble(v) + 3, "end", 9, MUTED, false, texts.formatInt(v)));
        }
        // Коридор ±10% вокруг нормы
        for (int i = 0; i < n; i++) {
            Integer target = days.get(i).target();
            if (target == null) continue;
            double top = y.applyAsDouble(target * 1.1);
            s.append(rect(pl + i * slot, top, slot + 0.5, y.applyAsDouble(target * 0.9) - top, SUCCESS_SOFT, 0));
        }
        for (int i = 0; i < n; i++) {
            DayStat d = days.get(i);
            double x = pl + i * slot + (slot - bw) / 2;
            if (d.logged()) {
                double top = y.applyAsDouble(d.calories());
                s.append(rect(x, top, bw, y.applyAsDouble(0) - top, ReportPalette.getBarColor(d.status()), 3));
            } else {
                s.append(String.format(Locale.ROOT,
                        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"5\" rx=\"2\" fill=\"none\" stroke=\"%s\" stroke-dasharray=\"2 2\"/>",
                        x, y.applyAsDouble(0) - 5, bw, SUBTLE));
            }
        }
        // Норма — ступенькой: она могла смениться внутри периода
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Integer target = days.get(i).target();
            if (target == null) continue;
            double yy = y.applyAsDouble(target);
            path.append(path.isEmpty() ? "M " : " L ").append(fmt(pl + i * slot)).append(' ').append(fmt(yy))
                    .append(" L ").append(fmt(pl + (i + 1) * slot)).append(' ').append(fmt(yy));
        }
        if (!path.isEmpty()) {
            s.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(TEXT)
                    .append("\" stroke-width=\"1.3\" stroke-dasharray=\"4 3\" opacity=\"0.55\"/>");
        }
        if (stats.targetChange() != null) {
            double x = pl + days.indexOf(stats.targetChange()) * slot;
            s.append(line(x, pt, x, pt + ph, SUBTLE, 1, "2 3"));
            s.append(text(x + 4, pt - 5, "start", 8.5, MUTED, false,
                    texts.get(CALORIES_NEW_TARGET, texts.formatInt(stats.targetChange().target()))));
        }
        for (int i = 0; i < n; i++) {
            LocalDate date = days.get(i).date();
            if (n <= DAILY_LABELS_MAX_DAYS || date.getDayOfWeek() == DayOfWeek.MONDAY) {
                s.append(text(pl + i * slot + slot / 2, h - 8, "middle", 9, MUTED, false, texts.formatDate(date, DATE_DAY)));
            }
        }
        return s.append("</svg>").toString();
    }

    // ── Вес ─────────────────────────────────────────────────────────

    public static String buildWeightChart(ReportStats stats, PdfTexts texts) {
        WeightStat weight = stats.weight();
        List<DayStat> days = stats.days();
        int n = days.size();
        boolean hasForecast = weight.forecastKg() != null;
        int total = n - 1 + (hasForecast ? stats.kind().getForecastDays() : 0);
        int h = 220, pl = 40, pr = hasForecast ? 76 : 12, pt = 16, pb = 26;
        double pw = WIDTH - pl - pr, ph = h - pt - pb;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (DayStat d : days) {
            if (d.weight() != null) {
                min = Math.min(min, d.weight().doubleValue());
                max = Math.max(max, d.weight().doubleValue());
            }
            if (d.trendWeight() != null) {
                min = Math.min(min, d.trendWeight());
                max = Math.max(max, d.trendWeight());
            }
        }
        if (hasForecast) {
            min = Math.min(min, weight.forecastKg() - 0.5);
            max = Math.max(max, weight.forecastKg() + 0.5);
        }
        int yMin = (int) Math.floor(min - 0.3), yMax = (int) Math.ceil(max + 0.3);
        int step = Math.max(1, (int) Math.ceil((yMax - yMin) / 5.0));
        DoubleUnaryOperator x = i -> pl + i / Math.max(total, 1) * pw;
        DoubleUnaryOperator y = v -> pt + ph - (v - yMin) / (yMax - yMin) * ph;

        StringBuilder s = openSvg(h);
        s.append("<defs><linearGradient id=\"wg\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"")
                .append(ACCENT).append("\" stop-opacity=\"0.22\"/><stop offset=\"1\" stop-color=\"").append(ACCENT)
                .append("\" stop-opacity=\"0\"/></linearGradient></defs>");
        for (int v = yMin; v <= yMax; v += step) {
            s.append(line(pl, y.applyAsDouble(v), WIDTH - pr, y.applyAsDouble(v), BORDER, 1, null));
            s.append(text(pl - 6, y.applyAsDouble(v) + 3, "end", 9, MUTED, false, texts.formatInt(v)));
        }

        double xEnd = x.applyAsDouble(n - 1), yEnd = y.applyAsDouble(weight.trendEnd());
        if (hasForecast) {
            double xf = x.applyAsDouble(total), yf = y.applyAsDouble(weight.forecastKg());
            // Конус неопределённости прогноза
            s.append(String.format(Locale.ROOT, "<path d=\"M %.1f %.1f L %.1f %.1f L %.1f %.1f Z\" fill=\"%s\" opacity=\"0.8\"/>",
                    xEnd, yEnd, xf, y.applyAsDouble(weight.forecastKg() + 0.45), xf, y.applyAsDouble(weight.forecastKg() - 0.45), ACCENT_SOFT));
            s.append(line(xEnd, yEnd, xf, yf, ACCENT, 2, "5 4"));
            s.append(line(xEnd, pt, xEnd, pt + ph, SUBTLE, 1, "2 3"));
            s.append(text(xEnd + 4, pt + 9, "start", 8.5, MUTED, false, texts.get(WEIGHT_TODAY)));
        }

        StringBuilder trend = new StringBuilder();
        int firstTrend = -1;
        for (int i = 0; i < n; i++) {
            Double t = days.get(i).trendWeight();
            if (t == null) continue;
            if (firstTrend < 0) firstTrend = i;
            trend.append(trend.isEmpty() ? "M " : " L ").append(fmt(x.applyAsDouble(i))).append(' ').append(fmt(y.applyAsDouble(t)));
        }
        s.append("<path d=\"").append(trend).append(" L ").append(fmt(xEnd)).append(' ').append(fmt(pt + ph))
                .append(" L ").append(fmt(x.applyAsDouble(firstTrend))).append(' ').append(fmt(pt + ph)).append(" Z\" fill=\"url(#wg)\"/>");
        s.append("<path d=\"").append(trend).append("\" fill=\"none\" stroke=\"").append(ACCENT)
                .append("\" stroke-width=\"2.6\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");
        for (int i = 0; i < n; i++) {
            if (days.get(i).weight() == null) continue;
            s.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"2.8\" fill=\"#FFFFFF\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                    x.applyAsDouble(i), y.applyAsDouble(days.get(i).weight().doubleValue()), SUBTLE));
        }
        if (hasForecast) {
            double xf = x.applyAsDouble(total), yf = y.applyAsDouble(weight.forecastKg());
            s.append(String.format(Locale.ROOT, "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"%s\"/>", xf, yf, ACCENT));
            s.append(text(xf + 8, yf - 2, "start", 11, TEXT, true,
                    texts.formatDecimal(weight.forecastKg(), 1) + " " + texts.get(UNIT_KG)));
            s.append(text(xf + 8, yf + 11, "start", 8.5, MUTED, false,
                    texts.get(WEIGHT_FORECAST_TO, texts.formatDate(weight.forecastDate(), DATE_DAY))));
        }
        int labelStep = Math.max(1, (int) Math.round(total / 6.0));
        if (total >= 21) labelStep = 7;
        for (int i = 0; i <= total; i += labelStep) {
            s.append(text(x.applyAsDouble(i), h - 8, "middle", 9, MUTED, false,
                    texts.formatDate(stats.startDate().plusDays(i), DATE_DAY)));
        }
        return s.append("</svg>").toString();
    }

    // ── БЖУ ─────────────────────────────────────────────────────────

    /** Кольцо: снаружи — доли БЖУ в калориях по факту, внутри — по цели. */
    public static String buildMacrosDonut(ReportStats stats, PdfTexts texts) {
        MacroStat m = stats.macros();
        int size = 170;
        double c = size / 2.0;
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" font-family=\"Nunito\">",
                size, size, size, size));
        s.append(buildRing(c, 70, 16, m.avgProteins() * 4, m.avgFats() * 9, m.avgCarbs() * 4, 1));
        if (m.targetProteins() != null && m.targetFats() != null && m.targetCarbs() != null) {
            s.append(buildRing(c, 52, 6, m.targetProteins() * 4, m.targetFats() * 9, m.targetCarbs() * 4, 0.4));
        }
        s.append(text(c, c - 2, "middle", 18, TEXT, true, texts.formatInt(stats.avgCalories())));
        s.append(text(c, c + 13, "middle", 9, MUTED, false, texts.get(MACROS_PER_DAY)));
        return s.append("</svg>").toString();
    }

    /**
     * Кольцо из дуг-сегментов. Дуги — через path: stroke-dasharray на окружности Batik в PDF не рисует.
     */
    private static String buildRing(double c, double r, double width, double proteins, double fats, double carbs, double opacity) {
        double total = proteins + fats + carbs;
        if (total <= 0) return "";
        double gap = 1.5; // градусов между сегментами
        double angle = -90;
        StringBuilder s = new StringBuilder();
        double[] parts = {proteins, fats, carbs};
        String[] colors = {PROTEINS, FATS, CARBS};
        for (int i = 0; i < parts.length; i++) {
            double sweep = parts[i] / total * 360;
            double from = angle, to = angle + sweep - gap;
            angle += sweep;
            if (to <= from) continue;
            // Дуга больше 180° в SVG неоднозначна при совпадении концов — режем на две половины
            double mid = (from + to) / 2;
            s.append(String.format(Locale.ROOT,
                    "<path d=\"M %.2f %.2f A %.1f %.1f 0 0 1 %.2f %.2f A %.1f %.1f 0 0 1 %.2f %.2f\" fill=\"none\" stroke=\"%s\" stroke-opacity=\"%.2f\" stroke-width=\"%.1f\"/>",
                    c + r * Math.cos(Math.toRadians(from)), c + r * Math.sin(Math.toRadians(from)),
                    r, r, c + r * Math.cos(Math.toRadians(mid)), c + r * Math.sin(Math.toRadians(mid)),
                    r, r, c + r * Math.cos(Math.toRadians(to)), c + r * Math.sin(Math.toRadians(to)),
                    colors[i], opacity, width));
        }
        return s.toString();
    }

    // ── Ритм питания ────────────────────────────────────────────────

    public static String buildRhythmChart(ReportStats stats, PdfTexts texts) {
        RhythmStat rhythm = stats.rhythm();
        List<Integer> hourly = rhythm.hourlyCalories();
        int h = 150, pl = 8, pr = 8, pt = 30, pb = 22;
        double pw = WIDTH - pl - pr, ph = h - pt - pb, slot = pw / 24, bw = slot * 0.66;
        int max = hourly.stream().mapToInt(Integer::intValue).max().orElse(0);
        DoubleUnaryOperator x = hour -> pl + hour * slot;

        StringBuilder s = openSvg(h);
        s.append(line(pl, pt + ph, WIDTH - pr, pt + ph, BORDER, 1, null));
        // Окно питания: от среднего первого до среднего последнего приёма пищи
        double x1 = x.applyAsDouble(rhythm.firstMealMinutes() / 60.0), x2 = x.applyAsDouble(rhythm.lastMealMinutes() / 60.0);
        double hours = (rhythm.lastMealMinutes() - rhythm.firstMealMinutes()) / 60.0;
        String window = texts.get(RHYTHM_WINDOW, texts.formatMinutes(rhythm.firstMealMinutes()),
                texts.formatMinutes(rhythm.lastMealMinutes()), texts.formatDecimal(hours, 1));
        // Узкое окно не вместит подпись — растягиваем полосу под текст
        double minWidth = window.length() * 5.2;
        if (x2 - x1 < minWidth) {
            double mid = (x1 + x2) / 2;
            x1 = Math.max(pl, mid - minWidth / 2);
            x2 = Math.min(WIDTH - pr, x1 + minWidth);
        }
        s.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"6\" width=\"%.1f\" height=\"15\" rx=\"7.5\" fill=\"%s\"/>", x1, x2 - x1, ACCENT_SOFT));
        s.append(text((x1 + x2) / 2, 17, "middle", 9, ACCENT, true, window));
        for (int hour = 0; hour < 24; hour++) {
            int value = hourly.get(hour);
            if (value <= 0 || max <= 0) continue;
            double bh = (double) value / max * ph;
            boolean late = hour >= 21 || hour < 5;
            s.append(String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"3\" fill=\"%s\" fill-opacity=\"%s\"/>",
                    x.applyAsDouble(hour) + (slot - bw) / 2, pt + ph - bh, bw, bh, late ? LATE : ACCENT, late ? "1" : "0.85"));
        }
        for (int hour = 0; hour < 24; hour += 3) {
            s.append(text(x.applyAsDouble(hour) + slot / 2, h - 6, "middle", 9, MUTED, false, String.format("%02d:00", hour)));
        }
        return s.append("</svg>").toString();
    }

    // ── Вода ────────────────────────────────────────────────────────

    public static String buildWaterChart(ReportStats stats, PdfTexts texts) {
        WaterStat water = stats.water();
        List<DayStat> days = stats.days();
        int n = days.size();
        int h = 100, pl = 8, pr = 8, pt = 14, pb = 18;
        double pw = WIDTH - pl - pr, ph = h - pt - pb, slot = pw / n, bw = slot * 0.55;
        int max = Math.max(water.targetMl() * 13 / 10,
                days.stream().mapToInt(d -> d.waterMl() != null ? d.waterMl() : 0).max().orElse(0));
        DoubleUnaryOperator y = v -> pt + ph - v / max * ph;

        StringBuilder s = openSvg(h);
        for (int i = 0; i < n; i++) {
            Integer ml = days.get(i).waterMl();
            if (ml == null || ml <= 0) continue;
            double top = y.applyAsDouble(ml);
            s.append(rect(pl + i * slot + (slot - bw) / 2, top, bw, y.applyAsDouble(0) - top,
                    ml >= water.targetMl() ? WATER : WATER_SOFT, 3));
        }
        double yt = y.applyAsDouble(water.targetMl());
        s.append(line(pl, yt, WIDTH - pr, yt, ACCENT, 1.3, "4 3"));
        s.append(text(WIDTH - pr, yt - 4, "end", 8.5, ACCENT, false, texts.get(WATER_TARGET, texts.formatInt(water.targetMl()))));
        for (int i = 0; i < n; i++) {
            LocalDate date = days.get(i).date();
            if (n <= DAILY_LABELS_MAX_DAYS || date.getDayOfWeek() == DayOfWeek.MONDAY) {
                s.append(text(pl + i * slot + slot / 2, h - 4, "middle", 9, MUTED, false, texts.formatDate(date, DATE_DAY)));
            }
        }
        return s.append("</svg>").toString();
    }

    // ── SVG-примитивы ───────────────────────────────────────────────

    private static StringBuilder openSvg(int height) {
        return new StringBuilder(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" font-family=\"Nunito\">",
                WIDTH, height, WIDTH, height));
    }

    private static String rect(double x, double y, double w, double h, String fill, double radius) {
        return String.format(Locale.ROOT, "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"%.1f\" fill=\"%s\"/>",
                x, y, w, Math.max(h, 0), radius, fill);
    }

    private static String line(double x1, double y1, double x2, double y2, String stroke, double width, String dash) {
        return String.format(Locale.ROOT, "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"%.1f\"%s/>",
                x1, y1, x2, y2, stroke, width, dash != null ? " stroke-dasharray=\"" + dash + "\"" : "");
    }

    private static String text(double x, double y, String anchor, double size, String fill, boolean bold, String value) {
        return String.format(Locale.ROOT, "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\" font-size=\"%.1f\" fill=\"%s\"%s>%s</text>",
                x, y, anchor, size, fill, bold ? " font-family=\"Nunito ExtraBold\"" : "", escapeHtml(value));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
