package com.kuklin.manageapp.bots.caloriebot.i18n;

/**
 * Ключи текстов бота — словари i18n/calorie/bot_<code>.properties.
 * В коде передаём только эти константы, не строки: опечатку в ключе поймает TextBundlesConsistencyTest.
 */
public final class BotTextKeys {
    private BotTextKeys() {
    }

    // ── /lang (только для разработчиков) ───────────────────────────
    public static final String DEV_LANG_CURRENT = "dev.lang.current";
    public static final String DEV_LANG_CURRENT_DEFAULT = "dev.lang.current-default";
    public static final String DEV_LANG_CHANGED = "dev.lang.changed";
    public static final String DEV_LANG_RESET = "dev.lang.reset";
    public static final String DEV_LANG_UNKNOWN = "dev.lang.unknown";

    // ── Кнопки главного меню (CalorieMenuButtons) ──────────────────
    public static final String BUTTON_TODAY = "button.today";
    public static final String BUTTON_WATER = "button.water";
    public static final String BUTTON_FAVORITES = "button.favorites";
    public static final String BUTTON_SETTINGS = "button.settings";
    public static final String BUTTON_PROFILE = "button.profile";
    public static final String BUTTON_REPORT = "button.report";
    public static final String BUTTON_FAQ = "button.faq";
    public static final String BUTTON_SUBSCRIPTION = "button.subscription";
    public static final String BUTTON_INVITE = "button.invite";

    // ── Рассылки ──────────────────────────────────────────────────
    public static final String REMINDER_MEAL = "reminder.meal";
}
