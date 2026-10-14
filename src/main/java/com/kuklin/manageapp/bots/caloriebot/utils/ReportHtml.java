package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.airesponse.ReportAiResponse;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportKind;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats;
import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.*;
import com.kuklin.manageapp.bots.caloriebot.texts.PdfTexts;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import static com.kuklin.manageapp.bots.caloriebot.i18n.PdfTextKeys.*;
import static com.kuklin.manageapp.bots.caloriebot.utils.ReportPalette.*;

/**
 * HTML-блоки PDF-отчёта. Каждый метод возвращает готовый раздел или пустую строку, если данных для него нет.
 * openhtmltopdf разбирает документ как XML: только закрытые теги, без &amp;nbsp; и прочих HTML-сущностей.
 * Вёрстка — таблицами и блоками: flex, grid и box-shadow рендерер не поддерживает.
 */
public final class ReportHtml {
    private ReportHtml() {
    }

    /** Разница с прошлым периодом меньше этого — не показываем стрелку */
    private static final double MIN_VISIBLE_CHANGE = 0.005;
    private static final double MAINTAIN_WEIGHT_TOLERANCE_KG = 0.5;
    /** Реальный расход отличается от формулы меньше — «почти как по формуле» */
    private static final int TDEE_CLOSE_KCAL = 100;
    /** Во сколько раз белка в выходные меньше, чтобы об этом сказать */
    private static final double PROTEIN_WEEKEND_GAP = 1.2;
    /** Категории, которые подсвечиваем акцентом на полосках */
    private static final Set<Dish.FoodCategory> ACCENT_CATEGORIES = EnumSet.of(Dish.FoodCategory.FAST_FOOD, Dish.FoodCategory.DESSERT);
    /** Сколько символов текста ИИ оставляем в карточке — длинный ответ ломает сетку */
    private static final int AI_TITLE_MAX = 40;
    private static final int AI_TEXT_MAX = 220;
    private static final int AI_SHORT_MAX = 60;

    // ── Обложка ─────────────────────────────────────────────────────

    public static String buildCover(ReportStats stats, ReportAiResponse ai, UserNutritionProfile profile,
                                    PdfTexts texts, String mascotSvg, ZonedDateTime generatedAt) {
        StringBuilder s = new StringBuilder();
        s.append("<table class=\"cover-top\"><tr>")
                .append("<td class=\"mascot\">").append(mascotSvg).append("</td>")
                .append("<td><div class=\"brand\">").append(escapeHtml(texts.get(BRAND))).append("</div>")
                .append("<h1>").append(escapeHtml(texts.get(stats.kind().getTitleKey()))).append("</h1>")
                .append("<div class=\"period\">").append(escapeHtml(texts.get(COVER_PERIOD,
                        texts.formatDate(stats.startDate(), DATE_LONG),
                        texts.formatDate(stats.endDate(), DATE_LONG_YEAR),
                        stats.days().size()))).append("</div></td>")
                .append("<td class=\"cover-meta\">").append(escapeHtml(texts.get(COVER_GENERATED))).append("<br/><b>")
                .append(escapeHtml(texts.formatDate(generatedAt, DATE_TIME))).append("</b></td>")
                .append("</tr></table>");

        s.append("<div class=\"chips\">").append(buildProfileTags(stats, profile, texts)).append("</div>");

        if (ai.headline() != null && !ai.headline().isBlank()) {
            s.append("<div class=\"headline\"><div class=\"lbl\">✨ ").append(escapeHtml(texts.get(HEADLINE_LABEL))).append("</div>")
                    .append("<div class=\"txt\">").append(escapeHtml(cut(ai.headline(), AI_TEXT_MAX))).append("</div></div>");
        }
        if (stats.hasFood()) s.append(buildKpis(stats, profile, texts));
        return s.toString();
    }

    private static String buildProfileTags(ReportStats stats, UserNutritionProfile profile, PdfTexts texts) {
        StringBuilder s = new StringBuilder();
        if (profile.getGoal() != null) {
            s.append(buildTag("🎯", texts.get(TAG_GOAL), texts.get(profile.getGoal().getTextKey())));
        }
        Integer firstTarget = stats.days().stream().map(DayStat::target).filter(Objects::nonNull).findFirst().orElse(null);
        Integer lastTarget = stats.days().get(stats.days().size() - 1).target();
        if (lastTarget != null) {
            String value = firstTarget != null && !firstTarget.equals(lastTarget)
                    ? texts.formatInt(firstTarget) + " → " + texts.formatInt(lastTarget)
                    : texts.formatInt(lastTarget);
            s.append(buildTag("🔥", texts.get(TAG_TARGET), value + " " + texts.get(UNIT_KCAL)));
        }
        if (stats.weight() != null) {
            s.append(buildTag("⚖️", texts.get(TAG_WEIGHT), texts.formatDecimal(stats.weight().trendStart(), 1) + " → "
                    + texts.formatDecimal(stats.weight().trendEnd(), 1) + " " + texts.get(UNIT_KG)));
        } else if (profile.getCurrentWeightKg() != null) {
            s.append(buildTag("⚖️", texts.get(TAG_WEIGHT),
                    texts.formatDecimal(profile.getCurrentWeightKg().doubleValue(), 1) + " " + texts.get(UNIT_KG)));
        }
        if (profile.getActivityLevel() != null) {
            s.append(buildTag("🏃", texts.get(TAG_ACTIVITY), texts.get(profile.getActivityLevel().getTextKey()).toLowerCase(texts.language().getLocale())));
        }
        return s.toString();
    }

    private static String buildTag(String emoji, String label, String value) {
        return "<span class=\"tag\">" + emoji + " " + escapeHtml(label) + ": <b>" + escapeHtml(value) + "</b></span>";
    }

    private static String buildKpis(ReportStats stats, UserNutritionProfile profile, PdfTexts texts) {
        UserNutritionProfile.Goal goal = profile.getGoal();
        List<String> tiles = new ArrayList<>();

        // 1. Средние калории и сравнение с прошлым периодом
        String caloriesChip = null, caloriesTone = "neutral";
        if (stats.prevAvgCalories() != null && stats.prevAvgCalories() > 0) {
            double change = (double) stats.avgCalories() / stats.prevAvgCalories() - 1;
            if (Math.abs(change) >= MIN_VISIBLE_CHANGE) {
                String percent = texts.formatDecimal(Math.abs(change) * 100, 1);
                caloriesChip = texts.get(change < 0 ? KPI_VS_PREV_DOWN : KPI_VS_PREV_UP, percent);
                caloriesTone = getToneForChange(change, goal);
            }
        }
        tiles.add(buildKpi("🔥", texts.formatInt(stats.avgCalories()), texts.get(UNIT_KCAL), texts.get(KPI_AVG_CALORIES), caloriesChip, caloriesTone));

        // 2. Дни в коридоре нормы
        int corridorPercent = (int) Math.round(stats.inCorridorDays() * 100.0 / stats.loggedDays());
        tiles.add(buildKpi("🎯", stats.inCorridorDays() + "<small>" + escapeHtml(texts.get(UNIT_OF)) + " " + stats.loggedDays() + "</small>", null,
                texts.get(KPI_CORRIDOR), texts.get(KPI_CORRIDOR_SHARE, corridorPercent), corridorPercent >= 60 ? "good" : "neutral"));

        // 3. Вес по тренду
        WeightStat weight = stats.weight();
        if (weight != null) {
            tiles.add(buildKpi("⚖️", texts.formatSigned(weight.deltaKg(), 1), texts.get(UNIT_KG), texts.get(KPI_WEIGHT),
                    texts.get(KPI_WEIGHT_RATE, texts.formatSigned(weight.ratePerWeek(), 1)), getWeightTone(weight.deltaKg(), goal)));
        } else {
            tiles.add(buildKpi("⚖️", "—", null, texts.get(KPI_WEIGHT), texts.get(KPI_WEIGHT_EMPTY), "neutral"));
        }

        // 4. Реальный расход, а без него — белок на кг веса
        MacroStat macros = stats.macros();
        if (stats.realTdee() != null) {
            String chip = stats.formulaTdee() != null ? texts.get(KPI_TDEE_FORMULA, texts.formatInt(stats.formulaTdee())) : null;
            tiles.add(buildKpi("⚡", "≈ " + texts.formatInt(stats.realTdee()), texts.get(UNIT_KCAL), texts.get(KPI_TDEE), chip, "warn"));
        } else if (macros != null && macros.proteinPerKg() != null) {
            boolean enough = macros.proteinPerKg() >= 1.6;
            tiles.add(buildKpi("🥩", texts.formatDecimal(macros.proteinPerKg(), 1), texts.get(UNIT_G), texts.get(KPI_PROTEIN),
                    texts.get(KPI_PROTEIN_AIM), enough ? "good" : "warn"));
        } else {
            tiles.add(buildKpi("🥩", "—", null, texts.get(KPI_PROTEIN), texts.get(KPI_NO_DATA), "neutral"));
        }

        // 5. Регулярность записей
        tiles.add(buildKpi("📒", stats.loggedDays() + "<small>" + escapeHtml(texts.get(UNIT_OF)) + " " + stats.days().size() + "</small>", null,
                texts.get(KPI_LOGGED), stats.streakDays() > 1 ? texts.get(KPI_STREAK, stats.streakDays()) : null, "good"));

        // 6. Вода
        WaterStat water = stats.water();
        if (water != null) {
            int percent = (int) Math.round(water.avgMl() * 100.0 / water.targetMl());
            tiles.add(buildKpi("💧", percent + "%", null, texts.get(KPI_WATER),
                    texts.get(KPI_WATER_DAYS, water.daysMet(), water.daysCounted()), percent >= 90 ? "good" : "warn"));
        } else {
            tiles.add(buildKpi("💧", "—", null, texts.get(KPI_WATER), texts.get(KPI_NO_DATA), "neutral"));
        }

        return "<div class=\"row\"><table class=\"grid\"><tr>" + String.join("", tiles.subList(0, 3)) + "</tr><tr>"
                + String.join("", tiles.subList(3, 6)) + "</tr></table></div>";
    }

    /** Хорошо ли изменение для цели: при похудении снижение — хорошо, при наборе — наоборот. */
    private static String getToneForChange(double change, UserNutritionProfile.Goal goal) {
        if (goal == null || goal == UserNutritionProfile.Goal.MAINTAIN) return "neutral";
        boolean wanted = goal == UserNutritionProfile.Goal.LOSE_WEIGHT ? change < 0 : change > 0;
        return wanted ? "good" : "warn";
    }

    /** Вес: при поддержании хорошо держаться в пределах ±0,5 кг, заметный уход в любую сторону — сигнал. */
    private static String getWeightTone(double deltaKg, UserNutritionProfile.Goal goal) {
        if (goal == UserNutritionProfile.Goal.MAINTAIN) {
            return Math.abs(deltaKg) <= MAINTAIN_WEIGHT_TOLERANCE_KG ? "good" : "warn";
        }
        return getToneForChange(deltaKg, goal);
    }

    /** value может содержать разметку &lt;small&gt; — экранируется вызывающим. */
    private static String buildKpi(String emoji, String value, String unit, String label, String chip, String tone) {
        return "<td><div class=\"kpi\"><div class=\"kpi-e\">" + emoji + "</div>"
                + "<div class=\"kpi-v\">" + value + (unit != null ? "<small>" + escapeHtml(unit) + "</small>" : "") + "</div>"
                + "<div class=\"kpi-l\">" + escapeHtml(label) + "</div>"
                + (chip != null ? "<div class=\"chip " + tone + "\">" + escapeHtml(chip) + "</div>" : "")
                + "</div></td>";
    }

    // ── Календарь ───────────────────────────────────────────────────

    public static String buildCalendar(ReportStats stats, PdfTexts texts) {
        if (!stats.hasFood()) return "";
        Map<LocalDate, DayStat> byDate = stats.days().stream().collect(Collectors.toMap(DayStat::date, d -> d));
        LocalDate from = stats.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate to = stats.endDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        StringBuilder s = new StringBuilder("<div class=\"block\">")
                .append(buildSectionHeader("🗓️", texts.get(CALENDAR_TITLE), texts.get(CALENDAR_NOTE)))
                .append("<div class=\"row-sm\"><table class=\"heat\"><tr>");
        for (DayOfWeek day : DayOfWeek.values()) s.append("<th>").append(escapeHtml(texts.formatWeekday(day))).append("</th>");
        s.append("</tr>");
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.MONDAY) s.append("<tr>");
            DayStat day = byDate.get(date);
            String number = "<div class=\"hd\">" + date.getDayOfMonth() + "</div>";
            if (day == null) {
                s.append("<td class=\"outside\">").append(number).append("</td>");
            } else if (!day.logged()) {
                s.append("<td class=\"none\">").append(number).append("<div class=\"hk\">")
                        .append(escapeHtml(texts.get(CALENDAR_NONE))).append("</div></td>");
            } else {
                String delta = "";
                if (day.target() != null && day.target() > 0) {
                    int percent = (int) Math.round((day.calories() * 100.0 / day.target()) - 100);
                    delta = " <span>" + (percent > 0 ? "+" : percent < 0 ? "−" : "") + Math.abs(percent) + "%</span>";
                }
                s.append("<td style=\"background-color: ").append(getTintColor(day.status())).append("; color: ")
                        .append(getInkColor(day.status())).append(";\">").append(number)
                        .append("<div class=\"hk\"><b>").append(texts.formatInt(day.calories())).append("</b>").append(delta).append("</div></td>");
            }
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) s.append("</tr>");
        }
        s.append("</table></div>").append(buildLegend(texts)).append("</div><div class=\"page-break\"></div>");
        return s.toString();
    }

    private static String buildLegend(PdfTexts texts) {
        StringBuilder s = new StringBuilder("<div class=\"legend\">");
        Map<DayStatus, String> labels = new LinkedHashMap<>();
        labels.put(DayStatus.UNDER, LEGEND_UNDER);
        labels.put(DayStatus.OK, LEGEND_OK);
        labels.put(DayStatus.OVER, LEGEND_OVER);
        labels.put(DayStatus.BIG_OVER, LEGEND_BIG_OVER);
        labels.forEach((status, key) -> s.append("<span><i style=\"background-color: ").append(getBarColor(status)).append(";\"></i>")
                .append(escapeHtml(texts.get(key))).append("</span>"));
        s.append("<span><i class=\"dash\"></i>").append(escapeHtml(texts.get(LEGEND_NONE))).append("</span>");
        return s.append("</div>").toString();
    }

    // ── Калории ─────────────────────────────────────────────────────

    public static String buildCaloriesSection(ReportStats stats, PdfTexts texts) {
        if (!stats.hasFood()) return "";
        List<String> cells = new ArrayList<>();
        if (stats.weekdayAvgCalories() != null) {
            cells.add(buildStat(texts.formatInt(stats.weekdayAvgCalories()), texts.get(UNIT_KCAL), texts.get(STAT_WEEKDAY), null));
        }
        if (stats.weekendAvgCalories() != null) {
            cells.add(buildStat(texts.formatInt(stats.weekendAvgCalories()), texts.get(UNIT_KCAL), texts.get(STAT_WEEKEND), null));
        }
        if (stats.weekdayAvgCalories() != null && stats.weekendAvgCalories() != null) {
            int effect = stats.weekendAvgCalories() - stats.weekdayAvgCalories();
            cells.add(buildStat(texts.formatSigned(effect, 0), texts.get(UNIT_KCAL), texts.get(STAT_WEEKEND_EFFECT),
                    effect > 0 ? WARN_INK : SUCCESS_DARK));
        }
        DayStat peak = stats.days().stream().filter(DayStat::logged).max(Comparator.comparingInt(DayStat::calories)).orElseThrow();
        cells.add(buildStat(texts.formatInt(peak.calories()), texts.get(UNIT_KCAL),
                texts.get(STAT_MAX, texts.formatDate(peak.date(), DATE_DAY)), null));

        return "<div class=\"block\">" + buildSectionHeader("🔥", texts.get(CALORIES_TITLE), texts.get(CALORIES_NOTE))
                + "<div class=\"card\">" + ReportCharts.buildCaloriesChart(stats, texts) + buildStats(cells) + "</div></div>";
    }

    // ── Вес ─────────────────────────────────────────────────────────

    public static String buildWeightSection(ReportStats stats, PdfTexts texts) {
        WeightStat weight = stats.weight();
        String header = buildSectionHeader("⚖️", texts.get(WEIGHT_TITLE), weight != null ? texts.get(WEIGHT_NOTE) : null);
        if (weight == null) {
            return "<div class=\"block\">" + header + "<div class=\"callout\">" + escapeHtml(texts.get(WEIGHT_EMPTY)) + "</div></div>";
        }
        List<String> cells = new ArrayList<>();
        cells.add(buildStat(String.valueOf(weight.measurements()), null, texts.get(STAT_MEASUREMENTS), null));
        cells.add(buildStat(texts.formatDecimal(weight.trendEnd(), 1), texts.get(UNIT_KG), texts.get(STAT_TREND_NOW), null));
        cells.add(buildStat(texts.formatSigned(weight.ratePerWeek(), 1), texts.get(UNIT_KG_WEEK), texts.get(STAT_RATE), null));
        if (weight.forecastKg() != null) {
            cells.add(buildStat(texts.formatDecimal(weight.forecastKg(), 1), texts.get(UNIT_KG),
                    texts.get(STAT_FORECAST, texts.formatDate(weight.forecastDate(), DATE_DAY)), null));
        }
        return "<div class=\"block\">" + header + "<div class=\"card\">" + ReportCharts.buildWeightChart(stats, texts)
                + buildStats(cells) + "</div></div>";
    }

    // ── Реальный расход и выходные ──────────────────────────────────

    public static String buildEnergySection(ReportStats stats, UserNutritionProfile profile, PdfTexts texts) {
        String tdee = buildTdeeCard(stats, texts);
        String split = buildSplitCard(stats, profile.getGoal(), texts);
        if (tdee.isEmpty() && split.isEmpty()) return "";
        if (tdee.isEmpty() || split.isEmpty()) return "<div class=\"block gap\">" + tdee + split + "</div>";
        return "<div class=\"block gap\"><div class=\"row\"><table class=\"grid\"><tr><td style=\"width: 58%;\">" + tdee + "</td><td>" + split + "</td></tr></table></div></div>";
    }

    private static String buildTdeeCard(ReportStats stats, PdfTexts texts) {
        if (stats.realTdee() == null) return "";
        String avg = texts.formatInt(stats.avgCalories()), real = texts.formatInt(stats.realTdee());
        String text;
        if (stats.formulaTdee() == null) {
            text = texts.get(TDEE_NO_FORMULA, avg, real);
        } else {
            int diff = stats.realTdee() - stats.formulaTdee();
            String key = Math.abs(diff) < TDEE_CLOSE_KCAL ? TDEE_CLOSE : diff > 0 ? TDEE_HIGHER : TDEE_LOWER;
            text = texts.get(key, avg, real, texts.formatInt(stats.formulaTdee()));
        }
        return "<div class=\"tdee\"><div class=\"lbl\">⚡ " + escapeHtml(texts.get(TDEE_LABEL)) + "</div>"
                + "<div class=\"big\">≈ " + real + " <small>" + escapeHtml(texts.get(TDEE_PER_DAY)) + "</small></div>"
                + "<div class=\"txt\">" + escapeHtml(text) + "</div>"
                + "<div class=\"how\">" + escapeHtml(texts.get(TDEE_HOW)) + "</div></div>";
    }

    /** При наборе веса выходные сверх будней — не «съеденный дефицит», а плюс к цели; меньше в выходные — недобор. */
    private static String buildSplitCard(ReportStats stats, UserNutritionProfile.Goal goal, PdfTexts texts) {
        Integer wd = stats.weekdayAvgCalories(), we = stats.weekendAvgCalories();
        if (wd == null || we == null) return "";
        int scale = Math.max(Math.max(wd, we), stats.avgTarget() != null ? stats.avgTarget() : 0) * 115 / 100;
        StringBuilder s = new StringBuilder("<div class=\"card fill\"><div class=\"card-title\">")
                .append(escapeHtml(texts.get(SPLIT_TITLE))).append("</div><table class=\"cmp\">")
                .append(buildCompareRow(texts.get(SPLIT_WEEKDAYS), wd, scale, SUCCESS, texts.formatInt(wd), false))
                .append(buildCompareRow(texts.get(SPLIT_WEEKENDS), we, scale, ACCENT, texts.formatInt(we), false));
        if (stats.avgTarget() != null) {
            s.append(buildCompareRow(texts.get(SPLIT_TARGET), stats.avgTarget(), scale, SUBTLE, texts.formatInt(stats.avgTarget()), true));
        }
        s.append("</table>");
        int extra = (we - wd) * 2;
        String note;
        if (goal == UserNutritionProfile.Goal.GAIN_WEIGHT && extra < 0) {
            note = highlight(texts.get(SPLIT_GAIN_LESS, texts.formatInt(-extra)), texts.formatInt(-extra));
        } else if (goal == UserNutritionProfile.Goal.GAIN_WEIGHT && extra > 0) {
            note = highlight(texts.get(SPLIT_GAIN_MORE, texts.formatInt(extra)), "+" + texts.formatInt(extra));
        } else if (extra <= 0) {
            note = escapeHtml(texts.get(SPLIT_EVEN));
        } else if (stats.weekendShare() != null && stats.weekendShare() >= 1) {
            note = highlight(texts.get(SPLIT_ALL, texts.formatInt(extra)), "+" + texts.formatInt(extra));
        } else if (stats.weekendShare() != null) {
            note = highlight(texts.get(SPLIT_SHARE, texts.formatInt(extra), Math.round(stats.weekendShare() * 100)), "+" + texts.formatInt(extra));
        } else {
            note = highlight(texts.get(SPLIT_EXTRA, texts.formatInt(extra)), "+" + texts.formatInt(extra));
        }
        return s.append("<div class=\"callout\">").append(note).append("</div></div>").toString();
    }

    private static String buildCompareRow(String label, int value, int scale, String color, String text, boolean muted) {
        return "<tr><td class=\"cl" + (muted ? " mut" : "") + "\">" + escapeHtml(label) + "</td><td>" + buildTrack(value * 100.0 / scale, color)
                + "</td><td class=\"cv" + (muted ? " mut" : "") + "\">" + (muted ? text : "<b>" + text + "</b>") + "</td></tr>";
    }

    // ── БЖУ ─────────────────────────────────────────────────────────

    public static String buildMacrosSection(ReportStats stats, PdfTexts texts) {
        MacroStat m = stats.macros();
        if (m == null) return "";
        StringBuilder rows = new StringBuilder("<table class=\"macros\">")
                .append(buildMacroRow(texts.get(MACROS_PROTEINS), PROTEINS, m.avgProteins(), m.targetProteins(), texts))
                .append(buildMacroRow(texts.get(MACROS_FATS), FATS, m.avgFats(), m.targetFats(), texts))
                .append(buildMacroRow(texts.get(MACROS_CARBS), CARBS, m.avgCarbs(), m.targetCarbs(), texts))
                .append("</table>");
        if (m.proteinPerKg() != null) {
            String text = texts.get(MACROS_PROTEIN_KG, texts.formatDecimal(m.proteinPerKg(), 1));
            if (m.weekdayAvgProteins() != null && m.weekendAvgProteins() != null && m.weekendAvgProteins() > 0
                    && (double) m.weekdayAvgProteins() / m.weekendAvgProteins() >= PROTEIN_WEEKEND_GAP) {
                text += " " + texts.get(MACROS_PROTEIN_WEEKEND, texts.formatDecimal((double) m.weekdayAvgProteins() / m.weekendAvgProteins(), 1));
            }
            rows.append("<div class=\"callout\">🥩 ").append(highlight(text, texts.formatDecimal(m.proteinPerKg(), 1))).append("</div>");
        }
        return "<div class=\"block\">" + buildSectionHeader("🥩", texts.get(MACROS_TITLE), texts.get(MACROS_NOTE))
                + "<div class=\"card\"><table class=\"split\"><tr><td class=\"donut\">" + ReportCharts.buildMacrosDonut(stats, texts)
                + "</td><td>" + rows + "</td></tr></table></div></div>";
    }

    private static String buildMacroRow(String name, String color, int fact, Integer target, PdfTexts texts) {
        String unit = texts.get(UNIT_G);
        if (target == null || target <= 0) {
            return "<tr><td class=\"mn\"><i style=\"background-color: " + color + ";\"></i>" + escapeHtml(name) + "</td><td class=\"mb\">"
                    + buildTrack(100, color) + "</td><td class=\"mv\"><b>" + fact + "</b> " + escapeHtml(unit) + "</td><td class=\"mp\"></td></tr>";
        }
        int percent = (int) Math.round(fact * 100.0 / target);
        String tone = fact > target * 1.1 ? "bad" : percent < 85 ? "warn" : "good";
        return "<tr><td class=\"mn\"><i style=\"background-color: " + color + ";\"></i>" + escapeHtml(name) + "</td>"
                + "<td class=\"mb\">" + buildTrack(Math.min(percent, 100), color) + "</td>"
                + "<td class=\"mv\"><b>" + fact + "</b> / " + target + " " + escapeHtml(unit) + "</td>"
                + "<td class=\"mp " + tone + "\">" + percent + "%</td></tr>";
    }

    // ── Ритм питания ────────────────────────────────────────────────

    public static String buildRhythmSection(ReportStats stats, PdfTexts texts) {
        RhythmStat r = stats.rhythm();
        if (r == null) return "";
        List<String> cells = List.of(
                buildStat(texts.formatMinutes(r.firstMealMinutes()), null, texts.get(STAT_FIRST_MEAL), null),
                buildStat(texts.formatMinutes(r.lastMealMinutes()), null, texts.get(STAT_LAST_MEAL), null),
                buildStat(texts.formatDecimal(r.mealsPerDay(), 1), null, texts.get(STAT_MEALS), null),
                buildStat(Math.round(r.lateShare() * 100) + "%", null, texts.get(STAT_LATE), LATE)
        );
        return "<div class=\"block\">" + buildSectionHeader("🕐", texts.get(RHYTHM_TITLE), texts.get(RHYTHM_NOTE))
                + "<div class=\"card\">" + ReportCharts.buildRhythmChart(stats, texts) + buildStats(cells) + "</div></div>";
    }

    // ── Топы и категории ────────────────────────────────────────────

    public static String buildTopSection(ReportStats stats, PdfTexts texts) {
        if (stats.topByCount().isEmpty()) return "";
        StringBuilder byCount = new StringBuilder();
        for (DishCount d : stats.topByCount()) {
            byCount.append(buildTopRow(d, d.count() + "×", " · " + texts.formatInt(d.calories())));
        }
        StringBuilder byCalories = new StringBuilder();
        for (DishCount d : stats.topByCalories()) {
            byCalories.append(buildTopRow(d, texts.formatInt(d.calories()), " " + texts.get(UNIT_KCAL)));
        }
        return "<div class=\"block gap\"><div class=\"row\"><table class=\"grid\"><tr>"
                + "<td><div class=\"card fill\"><div class=\"card-title\">🔁 " + escapeHtml(texts.get(TOP_COUNT)) + "</div><table class=\"top\">" + byCount + "</table></div></td>"
                + "<td><div class=\"card fill\"><div class=\"card-title\">🔥 " + escapeHtml(texts.get(TOP_CALORIES)) + "</div><table class=\"top\">" + byCalories + "</table></div></td>"
                + "</tr></table></div></div>";
    }

    private static String buildTopRow(DishCount d, String value, String suffix) {
        return "<tr><td class=\"te\">" + (d.emoji() != null ? escapeHtml(d.emoji()) : "🍽️") + "</td><td>" + escapeHtml(cut(d.name(), AI_SHORT_MAX))
                + "</td><td class=\"tv\">" + escapeHtml(value) + "<small>" + escapeHtml(suffix) + "</small></td></tr>";
    }

    public static String buildCategoriesSection(ReportStats stats, PdfTexts texts) {
        if (stats.categories().isEmpty()) return "";
        double max = stats.categories().get(0).share();
        StringBuilder rows = new StringBuilder();
        for (CategoryShare c : stats.categories()) {
            String color = ACCENT_CATEGORIES.contains(c.category()) ? ACCENT : CATEGORY;
            rows.append("<tr><td class=\"cn\">").append(escapeHtml(texts.category(c.category()))).append("</td><td>")
                    .append(buildTrack(c.share() / max * 100, color)).append("</td><td class=\"cp\">")
                    .append(Math.round(c.share() * 100)).append("%</td></tr>");
        }
        return "<div class=\"block\">" + buildSectionHeader("🍽️", texts.get(CATEGORIES_TITLE), texts.get(CATEGORIES_NOTE))
                + "<div class=\"card\"><table class=\"cat\">" + rows + "</table></div></div>";
    }

    // ── Разбор ИИ ───────────────────────────────────────────────────

    public static String buildPatternsSection(ReportAiResponse ai, PdfTexts texts) {
        List<ReportAiResponse.Pattern> patterns = ai.patterns() != null ? ai.patterns() : List.of();
        if (patterns.isEmpty()) return "";
        StringBuilder s = new StringBuilder("<div class=\"block\">")
                .append(buildSectionHeaderHtml("🧠", escapeHtml(texts.get(PATTERNS_TITLE)) + buildAiBadge(texts), texts.get(PATTERNS_NOTE)))
                .append("<div class=\"row\"><table class=\"pats\">");
        // По две карточки в ряд; ячейки таблицы выравнивают высоту соседних карточек
        for (int i = 0; i < patterns.size(); i += 2) {
            s.append("<tr>").append(buildPatternCell(patterns.get(i)))
                    .append(i + 1 < patterns.size() ? buildPatternCell(patterns.get(i + 1)) : "<td class=\"empty\"></td>")
                    .append("</tr>");
        }
        return s.append("</table></div></div>").toString();
    }

    private static String buildPatternCell(ReportAiResponse.Pattern p) {
        String tone = switch (p.sentiment() != null ? p.sentiment() : "") {
            case "positive" -> "pos";
            case "negative" -> "neg";
            default -> "neu";
        };
        return "<td class=\"" + tone + "\"><div class=\"pat-h\">" + (p.emoji() != null ? "<span class=\"pat-e\">" + escapeHtml(p.emoji()) + "</span>" : "")
                + escapeHtml(cut(p.title(), AI_TITLE_MAX)) + "</div>"
                + "<div class=\"pat-t\">" + escapeHtml(cut(p.description(), AI_TEXT_MAX)) + "</div>"
                + (p.impact() != null ? "<div class=\"pat-i\">" + escapeHtml(cut(p.impact(), AI_SHORT_MAX)) + "</div>" : "")
                + "</td>";
    }

    public static String buildSwapsSection(ReportAiResponse ai, PdfTexts texts) {
        List<ReportAiResponse.Swap> swaps = ai.swaps() != null ? ai.swaps() : List.of();
        if (swaps.isEmpty()) return "";
        StringBuilder rows = new StringBuilder();
        for (ReportAiResponse.Swap swap : swaps) {
            rows.append("<tr><td class=\"sw-from\">").append(escapeHtml(cut(swap.from(), AI_SHORT_MAX))).append("</td>")
                    .append("<td class=\"sw-arrow\">→</td>")
                    .append("<td class=\"sw-to\">").append(escapeHtml(cut(swap.to(), AI_SHORT_MAX))).append("</td>")
                    .append("<td class=\"sw-save\">").append(escapeHtml(cut(swap.saving(), AI_SHORT_MAX))).append("</td></tr>");
        }
        return "<div class=\"block\">" + buildSectionHeaderHtml("🔄", escapeHtml(texts.get(SWAPS_TITLE)) + buildAiBadge(texts), texts.get(SWAPS_NOTE))
                + "<div class=\"card\"><table class=\"swaps\">" + rows + "</table></div></div>";
    }

    private static String buildAiBadge(PdfTexts texts) {
        return "<span class=\"ai-badge\">" + escapeHtml(texts.get(AI_BADGE)) + "</span>";
    }

    // ── Вода ────────────────────────────────────────────────────────

    public static String buildWaterSection(ReportStats stats, PdfTexts texts) {
        WaterStat w = stats.water();
        if (w == null) return "";
        List<String> cells = new ArrayList<>();
        cells.add(buildStat(texts.formatInt(w.avgMl()), texts.get(UNIT_ML), texts.get(STAT_WATER_AVG), null));
        cells.add(buildStat(w.daysMet() + "<small> " + escapeHtml(texts.get(UNIT_OF)) + " " + w.daysCounted() + "</small>", null,
                texts.get(STAT_WATER_DAYS), null));
        if (w.avgMl() < w.targetMl()) {
            cells.add(buildStat("+" + texts.formatInt(w.targetMl() - w.avgMl()), texts.get(UNIT_ML), texts.get(STAT_WATER_GAP), null));
        }
        return "<div class=\"block\">" + buildSectionHeader("💧", texts.get(WATER_TITLE), texts.get(WATER_NOTE))
                + "<div class=\"card\">" + ReportCharts.buildWaterChart(stats, texts) + buildStats(cells) + "</div></div>";
    }

    // ── По дням ─────────────────────────────────────────────────────

    public static String buildDailySection(ReportStats stats, PdfTexts texts) {
        if (!stats.hasFood()) return "";
        // Месяц — таблица с новой страницы; неделя короткая и идёт следом
        StringBuilder s = new StringBuilder(stats.kind() == ReportKind.MONTH ? "<div class=\"page-break\"></div>" : "<div class=\"gap\"></div>")
                .append(buildSectionHeader("📋", texts.get(DAILY_TITLE), texts.get(DAILY_NOTE)))
                .append("<table class=\"daily\"><thead><tr>");
        for (String key : List.of(COL_DATE, COL_CALORIES, COL_TARGET, COL_DELTA, COL_PROTEINS, COL_FATS, COL_CARBS, COL_WATER, COL_WEIGHT)) {
            s.append("<th>").append(escapeHtml(texts.get(key))).append("</th>");
        }
        s.append("</tr></thead><tbody>");
        for (DayStat d : stats.days()) {
            String date = escapeHtml(texts.formatDate(d.date(), DATE_DAY)) + " <span class=\"mut\">" + escapeHtml(texts.formatWeekday(d.date().getDayOfWeek())) + "</span>";
            if (!d.logged()) {
                s.append("<tr class=\"miss\"><td>").append(date).append("</td><td colspan=\"8\">")
                        .append(escapeHtml(texts.get(DAILY_NONE))).append("</td></tr>");
                continue;
            }
            String delta = "—";
            if (d.target() != null) {
                delta = "<span class=\"pill\" style=\"background-color: " + getTintColor(d.status()) + "; color: " + getInkColor(d.status()) + ";\">"
                        + texts.formatSigned(d.calories() - d.target(), 0) + "</span>";
            }
            s.append("<tr").append(d.weekend() ? " class=\"we\"" : "").append("><td>").append(date).append("</td>")
                    .append("<td><b>").append(texts.formatInt(d.calories())).append("</b></td>")
                    .append("<td class=\"mut\">").append(d.target() != null ? texts.formatInt(d.target()) : "—").append("</td>")
                    .append("<td>").append(delta).append("</td>")
                    .append("<td>").append(d.proteins()).append("</td>")
                    .append("<td>").append(d.fats()).append("</td>")
                    .append("<td>").append(d.carbs()).append("</td>")
                    .append("<td>").append(d.waterMl() != null ? texts.formatInt(d.waterMl()) : "<span class=\"mut\">—</span>").append("</td>")
                    .append("<td>").append(d.weight() != null ? texts.formatDecimal(d.weight().doubleValue(), 1) : "<span class=\"mut\">—</span>").append("</td></tr>");
        }
        return s.append("</tbody></table>").toString();
    }

    /** Блюда недели по дням с итогом за день — для недельного отчёта. */
    public static String buildDishesSection(List<Dish> dishes, ZoneId zoneId, PdfTexts texts) {
        if (dishes.isEmpty()) return "";
        Map<LocalDate, List<Dish>> byDate = dishes.stream()
                .filter(d -> d.getCreated() != null)
                .sorted(Comparator.comparing(Dish::getCreated))
                .collect(Collectors.groupingBy(d -> d.getCreated().atZone(zoneId).toLocalDate(), TreeMap::new, Collectors.toList()));

        StringBuilder s = new StringBuilder("<div class=\"gap\"></div>")
                .append(buildSectionHeader("🍽️", texts.get(DISHES_TITLE), texts.get(DISHES_NOTE)))
                .append("<table class=\"daily dishes\"><thead><tr><th>").append(escapeHtml(texts.get(COL_DATE))).append("</th><th></th><th>")
                .append(escapeHtml(texts.get(COL_CALORIES))).append("</th><th>").append(escapeHtml(texts.get(COL_PROTEINS))).append("</th><th>")
                .append(escapeHtml(texts.get(COL_FATS))).append("</th><th>").append(escapeHtml(texts.get(COL_CARBS))).append("</th></tr></thead><tbody>");
        for (Map.Entry<LocalDate, List<Dish>> day : byDate.entrySet()) {
            int calories = 0, proteins = 0, fats = 0, carbs = 0;
            for (Dish d : day.getValue()) {
                String time = d.getCreated().atZone(zoneId).toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString();
                s.append("<tr><td class=\"mut\">").append(time).append("</td><td>")
                        .append(d.getEmojiIcon() != null ? escapeHtml(d.getEmojiIcon()) + " " : "")
                        .append(escapeHtml(cut(d.getName(), AI_SHORT_MAX))).append("</td><td><b>").append(nvl(d.getCalories())).append("</b></td><td>")
                        .append(nvl(d.getProteins())).append("</td><td>").append(nvl(d.getFats())).append("</td><td>")
                        .append(nvl(d.getCarbohydrates())).append("</td></tr>");
                calories += nvl(d.getCalories());
                proteins += nvl(d.getProteins());
                fats += nvl(d.getFats());
                carbs += nvl(d.getCarbohydrates());
            }
            s.append("<tr class=\"total\"><td>").append(escapeHtml(texts.formatDate(day.getKey(), DATE_DAY))).append(" ")
                    .append(escapeHtml(texts.formatWeekday(day.getKey().getDayOfWeek()))).append("</td><td>")
                    .append(escapeHtml(texts.get(DISHES_TOTAL))).append("</td><td>").append(texts.formatInt(calories)).append("</td><td>")
                    .append(proteins).append("</td><td>").append(fats).append("</td><td>").append(carbs).append("</td></tr>");
        }
        return s.append("</tbody></table>").toString();
    }

    // ── Точность данных и пустой отчёт ──────────────────────────────

    public static String buildQualitySection(ReportStats stats, PdfTexts texts) {
        if (!stats.hasFood()) return "";
        String text = stats.avgAiConfidence() != null
                ? texts.get(QUALITY_TEXT, stats.avgAiConfidence(), stats.loggedDays(), stats.days().size())
                : texts.get(QUALITY_TEXT_NO_AI, stats.loggedDays(), stats.days().size());
        if (stats.loggedDays() < stats.days().size()) text += " " + texts.get(QUALITY_GAPS);
        return "<div class=\"block\">" + buildSectionHeader("🎯", texts.get(QUALITY_TITLE), null)
                + "<div class=\"callout flat\">📸 " + escapeHtml(text) + "</div></div>";
    }

    public static String buildEmpty(ReportStats stats, PdfTexts texts) {
        if (stats.hasFood()) return "";
        return "<div class=\"callout empty\">🍽️ " + escapeHtml(texts.get(EMPTY)) + "</div>";
    }

    // ── Общие кусочки ───────────────────────────────────────────────

    private static String buildSectionHeader(String emoji, String title, String note) {
        return buildSectionHeaderHtml(emoji, escapeHtml(title), note);
    }

    /** titleHtml — уже экранированный заголовок (может содержать значок ИИ). */
    private static String buildSectionHeaderHtml(String emoji, String titleHtml, String note) {
        return "<div class=\"sec\"><h2><span class=\"se\">" + emoji + "</span>" + titleHtml + "</h2>"
                + (note != null ? "<div class=\"note\">" + escapeHtml(note) + "</div>" : "") + "</div>";
    }

    /** Ячейки цифр под графиком; value может содержать &lt;small&gt;. */
    private static String buildStat(String value, String unit, String label, String color) {
        return "<td><div class=\"stat-v\"" + (color != null ? " style=\"color: " + color + ";\"" : "") + ">" + value
                + (unit != null ? " <small>" + escapeHtml(unit) + "</small>" : "") + "</div>"
                + "<div class=\"stat-l\">" + escapeHtml(label) + "</div></td>";
    }

    private static String buildStats(List<String> cells) {
        return "<table class=\"stats\"><tr>" + String.join("", cells) + "</tr></table>";
    }

    private static String buildTrack(double percent, String color) {
        return String.format(Locale.ROOT, "<div class=\"track\"><div class=\"fillb\" style=\"width: %.1f%%; background-color: %s;\"></div></div>",
                Math.max(0, Math.min(100, percent)), color);
    }

    /** Экранирует текст и выделяет в нём фрагмент акцентом. */
    private static String highlight(String text, String fragment) {
        String escaped = escapeHtml(text);
        String escapedFragment = escapeHtml(fragment);
        int at = escaped.indexOf(escapedFragment);
        if (at < 0) return escaped;
        return escaped.substring(0, at) + "<b>" + escapedFragment + "</b>" + escaped.substring(at + escapedFragment.length());
    }

    private static String cut(String text, int max) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1).trim() + "…";
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Экранирует текст для XHTML: спецсимволы и управляющие символы, которые XML-парсер рендерера не пропустит.
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\n' || c == '\t') sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
