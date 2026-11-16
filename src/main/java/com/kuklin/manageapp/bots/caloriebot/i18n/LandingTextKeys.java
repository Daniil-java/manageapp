package com.kuklin.manageapp.bots.caloriebot.i18n;

/**
 * Ключи текстов лендинга — словари i18n/calorie/landing_<code>.properties.
 * Тексты страницы шаблон берёт картой из LandingTexts (${t['…']}, T["…"] в скрипте),
 * здесь — только ключи, которые форматирует код (таблица тарифов в CalorieLandingService).
 */
public final class LandingTextKeys {
    private LandingTextKeys() {
    }

    // ── Таблица тарифов ─────────────────────────────────────────────
    public static final String LIMIT_AI_REQUESTS = "landing.limit.ai-requests";
    public static final String LIMIT_AI_PER_DAY = "landing.limit.ai-per-day";
    public static final String LIMIT_UNLIMITED = "landing.limit.unlimited";
    public static final String LIMIT_DAILY = "landing.limit.daily";
    public static final String LIMIT_MONTHLY = "landing.limit.monthly";
    public static final String LIMIT_LIFETIME = "landing.limit.lifetime";

    // ── Карточки тарифов ────────────────────────────────────────────
    public static final String PLAN_DURATION_MONTHS = "landing.plan.duration-months";
    public static final String PLAN_DURATION_DAYS = "landing.plan.duration-days";
}
