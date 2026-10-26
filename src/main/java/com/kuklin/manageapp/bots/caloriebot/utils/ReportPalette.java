package com.kuklin.manageapp.bots.caloriebot.utils;

import com.kuklin.manageapp.bots.caloriebot.models.report.ReportStats.DayStatus;

/**
 * Цвета PDF-отчёта — светлая тема мини-приложения (zef-fe, globals.css).
 * CSS шаблона повторяет эти же значения; графики и блоки берут их отсюда.
 */
public final class ReportPalette {
    private ReportPalette() {
    }

    public static final String BG_SOFT = "#F5EFE6";
    public static final String SURFACE_2 = "#FBF6EE";
    public static final String TEXT = "#3E2723";
    public static final String MUTED = "#8A7A70";
    public static final String SUBTLE = "#C9BCB2";
    public static final String BORDER = "#EFE6D9";
    public static final String ACCENT = "#D97A4A";
    public static final String ACCENT_SOFT = "#FBE7D9";
    public static final String SUCCESS = "#7FB77E";
    public static final String SUCCESS_SOFT = "#E6F3E2";
    public static final String SUCCESS_DARK = "#4F7F4E";
    public static final String WARN_INK = "#C96A3A";

    // БЖУ — как полоски на главной мини-приложения (DailyGoalCard)
    public static final String PROTEINS = "#D97A4A";
    public static final String FATS = "#8F6A4C";
    public static final String CARBS = "#D99246";
    /** Поздние калории на графике ритма */
    public static final String LATE = "#8F4A2A";
    /** Вода — как WaterTrendCard */
    public static final String WATER = "#5b9bd5";
    public static final String WATER_SOFT = "#a8c8e8";
    /** Полоски категорий: обычные и «на заметку» (фастфуд, десерты) */
    public static final String CATEGORY = "#E3B48F";

    /** Цвет столбика калорий и пометки в легенде */
    public static String getBarColor(DayStatus status) {
        return switch (status) {
            case UNDER -> "#D8C8B0";
            case OK -> SUCCESS;
            case OVER -> "#EBA57E";
            case BIG_OVER -> "#D2603A";
            case NONE -> SUBTLE;
        };
    }

    /** Фон клетки календаря и плашки Δ */
    public static String getTintColor(DayStatus status) {
        return switch (status) {
            case UNDER -> "#F3ECE1";
            case OK -> SUCCESS_SOFT;
            case OVER -> ACCENT_SOFT;
            case BIG_OVER -> "#F4C7AE";
            case NONE -> SURFACE_2;
        };
    }

    /** Текст на фоне getTintColor */
    public static String getInkColor(DayStatus status) {
        return switch (status) {
            case UNDER, NONE -> MUTED;
            case OK -> SUCCESS_DARK;
            case OVER -> WARN_INK;
            case BIG_OVER -> LATE;
        };
    }
}
