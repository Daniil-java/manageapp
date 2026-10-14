package com.kuklin.manageapp.bots.caloriebot.i18n;

/**
 * Ключи текстов PDF-отчётов — словари i18n/calorie/pdf_<code>.properties.
 * Тексты шаблона (templates/report.html) подставляются по {{pdf.…}} прямо из словаря,
 * здесь — только ключи, которые нужны в коде (блоки отчёта, графики, отправка в боте).
 */
public final class PdfTextKeys {
    private PdfTextKeys() {
    }

    // ── Форматы дат (DateTimeFormatter) ─────────────────────────────
    public static final String DATE = "pdf.date";
    public static final String DATE_DAY = "pdf.date-day";
    public static final String DATE_LONG = "pdf.date-long";
    public static final String DATE_LONG_YEAR = "pdf.date-long-year";
    public static final String DATE_TIME = "pdf.date-time";

    // ── Обложка ─────────────────────────────────────────────────────
    public static final String BRAND = "pdf.brand";
    public static final String TITLE_WEEK = "pdf.title.week";
    public static final String TITLE_MONTH = "pdf.title.month";
    public static final String COVER_PERIOD = "pdf.cover.period";
    public static final String COVER_GENERATED = "pdf.cover.generated";
    public static final String TAG_GOAL = "pdf.tag.goal";
    public static final String TAG_TARGET = "pdf.tag.target";
    public static final String TAG_WEIGHT = "pdf.tag.weight";
    public static final String TAG_ACTIVITY = "pdf.tag.activity";
    public static final String HEADLINE_LABEL = "pdf.headline.label";

    // ── Единицы ─────────────────────────────────────────────────────
    public static final String UNIT_KCAL = "pdf.unit.kcal";
    public static final String UNIT_KG = "pdf.unit.kg";
    public static final String UNIT_G = "pdf.unit.g";
    public static final String UNIT_ML = "pdf.unit.ml";
    public static final String UNIT_OF = "pdf.unit.of";
    public static final String UNIT_KG_WEEK = "pdf.unit.kg-week";
    public static final String UNIT_HOURS = "pdf.unit.hours";

    // ── Карточки с главными цифрами ─────────────────────────────────
    public static final String KPI_AVG_CALORIES = "pdf.kpi.avg-calories";
    public static final String KPI_VS_PREV_DOWN = "pdf.kpi.vs-prev-down";
    public static final String KPI_VS_PREV_UP = "pdf.kpi.vs-prev-up";
    public static final String KPI_CORRIDOR = "pdf.kpi.corridor";
    public static final String KPI_CORRIDOR_SHARE = "pdf.kpi.corridor-share";
    public static final String KPI_WEIGHT = "pdf.kpi.weight";
    public static final String KPI_WEIGHT_RATE = "pdf.kpi.weight-rate";
    public static final String KPI_WEIGHT_EMPTY = "pdf.kpi.weight-empty";
    public static final String KPI_TDEE = "pdf.kpi.tdee";
    public static final String KPI_TDEE_FORMULA = "pdf.kpi.tdee-formula";
    public static final String KPI_PROTEIN = "pdf.kpi.protein";
    public static final String KPI_PROTEIN_AIM = "pdf.kpi.protein-aim";
    public static final String KPI_LOGGED = "pdf.kpi.logged";
    public static final String KPI_STREAK = "pdf.kpi.streak";
    public static final String KPI_WATER = "pdf.kpi.water";
    public static final String KPI_WATER_DAYS = "pdf.kpi.water-days";
    public static final String KPI_NO_DATA = "pdf.kpi.no-data";

    // ── Календарь ───────────────────────────────────────────────────
    public static final String CALENDAR_TITLE = "pdf.calendar.title";
    public static final String CALENDAR_NOTE = "pdf.calendar.note";
    public static final String CALENDAR_NONE = "pdf.calendar.none";
    public static final String LEGEND_UNDER = "pdf.legend.under";
    public static final String LEGEND_OK = "pdf.legend.ok";
    public static final String LEGEND_OVER = "pdf.legend.over";
    public static final String LEGEND_BIG_OVER = "pdf.legend.big-over";
    public static final String LEGEND_NONE = "pdf.legend.none";

    // ── Калории ─────────────────────────────────────────────────────
    public static final String CALORIES_TITLE = "pdf.calories.title";
    public static final String CALORIES_NOTE = "pdf.calories.note";
    public static final String CALORIES_NEW_TARGET = "pdf.calories.new-target";
    public static final String STAT_WEEKDAY = "pdf.stat.weekday";
    public static final String STAT_WEEKEND = "pdf.stat.weekend";
    public static final String STAT_WEEKEND_EFFECT = "pdf.stat.weekend-effect";
    public static final String STAT_MAX = "pdf.stat.max";

    // ── Вес ─────────────────────────────────────────────────────────
    public static final String WEIGHT_TITLE = "pdf.weight.title";
    public static final String WEIGHT_NOTE = "pdf.weight.note";
    public static final String WEIGHT_EMPTY = "pdf.weight.empty";
    public static final String WEIGHT_TODAY = "pdf.weight.today";
    public static final String WEIGHT_FORECAST_TO = "pdf.weight.forecast-to";
    public static final String STAT_MEASUREMENTS = "pdf.stat.measurements";
    public static final String STAT_TREND_NOW = "pdf.stat.trend-now";
    public static final String STAT_RATE = "pdf.stat.rate";
    public static final String STAT_FORECAST = "pdf.stat.forecast";

    // ── Реальный расход ─────────────────────────────────────────────
    public static final String TDEE_LABEL = "pdf.tdee.label";
    public static final String TDEE_PER_DAY = "pdf.tdee.per-day";
    public static final String TDEE_HIGHER = "pdf.tdee.higher";
    public static final String TDEE_LOWER = "pdf.tdee.lower";
    public static final String TDEE_CLOSE = "pdf.tdee.close";
    public static final String TDEE_NO_FORMULA = "pdf.tdee.no-formula";
    public static final String TDEE_HOW = "pdf.tdee.how";

    // ── Будни и выходные ────────────────────────────────────────────
    public static final String SPLIT_TITLE = "pdf.split.title";
    public static final String SPLIT_WEEKDAYS = "pdf.split.weekdays";
    public static final String SPLIT_WEEKENDS = "pdf.split.weekends";
    public static final String SPLIT_TARGET = "pdf.split.target";
    public static final String SPLIT_SHARE = "pdf.split.share";
    public static final String SPLIT_ALL = "pdf.split.all";
    public static final String SPLIT_EXTRA = "pdf.split.extra";
    public static final String SPLIT_EVEN = "pdf.split.even";

    // ── БЖУ ─────────────────────────────────────────────────────────
    public static final String MACROS_TITLE = "pdf.macros.title";
    public static final String MACROS_NOTE = "pdf.macros.note";
    public static final String MACROS_PER_DAY = "pdf.macros.per-day";
    public static final String MACROS_PROTEINS = "pdf.macros.proteins";
    public static final String MACROS_FATS = "pdf.macros.fats";
    public static final String MACROS_CARBS = "pdf.macros.carbs";
    public static final String MACROS_PROTEIN_KG = "pdf.macros.protein-kg";
    public static final String MACROS_PROTEIN_WEEKEND = "pdf.macros.protein-weekend";

    // ── Ритм питания ────────────────────────────────────────────────
    public static final String RHYTHM_TITLE = "pdf.rhythm.title";
    public static final String RHYTHM_NOTE = "pdf.rhythm.note";
    public static final String RHYTHM_WINDOW = "pdf.rhythm.window";
    public static final String STAT_FIRST_MEAL = "pdf.stat.first-meal";
    public static final String STAT_LAST_MEAL = "pdf.stat.last-meal";
    public static final String STAT_MEALS = "pdf.stat.meals";
    public static final String STAT_LATE = "pdf.stat.late";

    // ── Топы и категории ────────────────────────────────────────────
    public static final String TOP_COUNT = "pdf.top.count";
    public static final String TOP_CALORIES = "pdf.top.calories";
    public static final String CATEGORIES_TITLE = "pdf.categories.title";
    public static final String CATEGORIES_NOTE = "pdf.categories.note";

    // ── Разбор ИИ ───────────────────────────────────────────────────
    public static final String AI_BADGE = "pdf.ai.badge";
    public static final String PATTERNS_TITLE = "pdf.patterns.title";
    public static final String PATTERNS_NOTE = "pdf.patterns.note";
    public static final String SWAPS_TITLE = "pdf.swaps.title";
    public static final String SWAPS_NOTE = "pdf.swaps.note";

    // ── Вода ────────────────────────────────────────────────────────
    public static final String WATER_TITLE = "pdf.water.title";
    public static final String WATER_NOTE = "pdf.water.note";
    public static final String WATER_TARGET = "pdf.water.target";
    public static final String STAT_WATER_AVG = "pdf.stat.water-avg";
    public static final String STAT_WATER_DAYS = "pdf.stat.water-days";
    public static final String STAT_WATER_GAP = "pdf.stat.water-gap";

    // ── По дням ─────────────────────────────────────────────────────
    public static final String DAILY_TITLE = "pdf.daily.title";
    public static final String DAILY_NOTE = "pdf.daily.note";
    public static final String DAILY_NONE = "pdf.daily.none";
    public static final String COL_DATE = "pdf.col.date";
    public static final String COL_CALORIES = "pdf.col.calories";
    public static final String COL_TARGET = "pdf.col.target";
    public static final String COL_DELTA = "pdf.col.delta";
    public static final String COL_PROTEINS = "pdf.col.proteins";
    public static final String COL_FATS = "pdf.col.fats";
    public static final String COL_CARBS = "pdf.col.carbs";
    public static final String COL_WATER = "pdf.col.water";
    public static final String COL_WEIGHT = "pdf.col.weight";

    // ── Блюда по дням (неделя) ──────────────────────────────────────
    public static final String DISHES_TITLE = "pdf.dishes.title";
    public static final String DISHES_NOTE = "pdf.dishes.note";
    public static final String DISHES_TOTAL = "pdf.dishes.total";

    // ── Точность данных, пустой отчёт, подвал ───────────────────────
    public static final String QUALITY_TITLE = "pdf.quality.title";
    public static final String QUALITY_TEXT = "pdf.quality.text";
    public static final String QUALITY_TEXT_NO_AI = "pdf.quality.text-no-ai";
    public static final String QUALITY_GAPS = "pdf.quality.gaps";
    public static final String EMPTY = "pdf.empty";
    public static final String FOOTER = "pdf.footer";

    // ── Отправка отчёта в боте ──────────────────────────────────────
    public static final String CAPTION_DAY = "pdf.caption.day";
    public static final String CAPTION_WEEK = "pdf.caption.week";
    public static final String CAPTION_MONTH = "pdf.caption.month";
    public static final String CAPTION_WEIGHT = "pdf.caption.weight";
    public static final String GENERATING = "pdf.generating";
    public static final String FAILED = "pdf.failed";
}
