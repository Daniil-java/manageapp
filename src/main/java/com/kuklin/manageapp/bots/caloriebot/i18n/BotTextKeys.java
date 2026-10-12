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
}
