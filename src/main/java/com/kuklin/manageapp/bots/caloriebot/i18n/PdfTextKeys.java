package com.kuklin.manageapp.bots.caloriebot.i18n;

/**
 * Ключи текстов PDF-отчётов — словари i18n/calorie/pdf_<code>.properties.
 * Тексты шаблонов (templates/report.html, weeklyreport.html) подставляются по {{pdf.…}} прямо из словаря,
 * здесь — только ключи, которые нужны в коде (таблицы, диаграммы, разбор ИИ, отправка в боте).
 */
public final class PdfTextKeys {
    private PdfTextKeys() {
    }

    // ── Форматы дат (DateTimeFormatter) ─────────────────────────────
    public static final String DATE = "pdf.date";
    public static final String DATE_SHORT = "pdf.date-short";

    // ── Диаграммы ───────────────────────────────────────────────────
    public static final String CHART_CATEGORIES = "pdf.chart.categories";
    public static final String CHART_HOURS = "pdf.chart.hours";

    // ── Таблицы: названия ───────────────────────────────────────────
    public static final String TABLE_WEEKLY = "pdf.table.weekly";
    public static final String TABLE_DAILY = "pdf.table.daily";
    public static final String TABLE_DISHES = "pdf.table.dishes";
    public static final String TABLE_WEIGHT_HISTORY = "pdf.table.weight-history";

    // ── Таблицы: колонки ────────────────────────────────────────────
    public static final String COL_WEEK = "pdf.table.week";
    public static final String COL_DATE = "pdf.table.date";
    public static final String COL_DISH = "pdf.table.dish";
    public static final String COL_WEIGHT = "pdf.table.weight";
    public static final String COL_WEIGHT_KG = "pdf.table.weight-kg";
    public static final String COL_CHANGE = "pdf.table.change";
    public static final String COL_CALORIES = "pdf.table.calories";
    public static final String COL_CALORIE_TARGET = "pdf.table.calorie-target";
    public static final String COL_PROTEINS = "pdf.table.proteins";
    public static final String COL_FATS = "pdf.table.fats";
    public static final String COL_CARBS = "pdf.table.carbs";
    public static final String COL_AVG_CALORIES = "pdf.table.avg-calories";
    public static final String COL_AVG_PROTEINS = "pdf.table.avg-proteins";
    public static final String COL_AVG_FATS = "pdf.table.avg-fats";
    public static final String COL_AVG_CARBS = "pdf.table.avg-carbs";
    public static final String COL_AVG_WEIGHT = "pdf.table.avg-weight";

    // ── Разбор пищевого поведения от ИИ ─────────────────────────────
    public static final String ANALYSIS_MACRO_BALANCE = "pdf.analysis.macro-balance";
    public static final String ANALYSIS_SUMMARY = "pdf.analysis.summary";
    public static final String ANALYSIS_TOP_PRODUCTS = "pdf.analysis.top-products";
    public static final String ANALYSIS_UNHEALTHY = "pdf.analysis.unhealthy";
    public static final String ANALYSIS_CALORIE_SOURCES = "pdf.analysis.calorie-sources";
    public static final String ANALYSIS_HABITS = "pdf.analysis.habits";
    public static final String ANALYSIS_PATTERNS = "pdf.analysis.patterns";
    public static final String ANALYSIS_ROOT_CAUSE = "pdf.analysis.root-cause";
    public static final String ANALYSIS_RECOMMENDATIONS = "pdf.analysis.recommendations";

    // ── Отправка отчёта в боте ──────────────────────────────────────
    public static final String CAPTION_DAY = "pdf.caption.day";
    public static final String CAPTION_WEEK = "pdf.caption.week";
    public static final String CAPTION_MONTH = "pdf.caption.month";
    public static final String CAPTION_WEIGHT = "pdf.caption.weight";
    public static final String GENERATING = "pdf.generating";
    public static final String FAILED = "pdf.failed";
}
