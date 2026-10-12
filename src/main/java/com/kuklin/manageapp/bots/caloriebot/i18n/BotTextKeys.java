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

    // ── Общие подписи кнопок ───────────────────────────────────────
    public static final String COMMON_BUTTON_CLOSE = "common.button.close";
    public static final String COMMON_BUTTON_NEXT = "common.button.next";
    public static final String COMMON_BUTTON_INSTRUCTION = "common.button.instruction";

    // ── Онбординг: /start, знакомство, FAQ, /menu, /keyboard, /support ──
    public static final String START_WELCOME = "start.welcome";
    public static final String START_FLOW_INTRO = "start.flow.intro";
    public static final String START_FLOW_FILL_PROFILE = "start.flow.fill-profile";
    public static final String START_FLOW_BUTTON_FILL_PROFILE = "start.flow.button.fill-profile";
    public static final String WELCOME_TEXT = "welcome.text";
    public static final String MENU_TEXT = "menu.text";
    public static final String KEYBOARD_UPDATED = "keyboard.updated";
    public static final String SUPPORT_TEXT = "support.text";
    public static final String SUPPORT_BUTTON = "support.button";

    // ── Реферальная программа и UTM-ссылки ─────────────────────────
    public static final String REFERRAL_TEXT = "referral.text";
    public static final String REFERRAL_SHARE_TEXT = "referral.share-text";
    public static final String REFERRAL_BUTTON_SHARE = "referral.button.share";
    public static final String ADMIN_UTM_BUTTON_CREATE = "admin.utm.button.create";
    public static final String ADMIN_UTM_CREATED = "admin.utm.created";

    // ── Рассылки ──────────────────────────────────────────────────
    public static final String REMINDER_MEAL = "reminder.meal";
}
