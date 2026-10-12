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

    // ── Длительность ───────────────────────────────────────────────
    public static final String COMMON_DURATION_MINUTES = "common.duration.minutes";
    public static final String COMMON_DURATION_SECONDS = "common.duration.seconds";

    // ── Карточка блюда и кнопки под ней ────────────────────────────
    public static final String DISH_CARD_TITLE = "dish.card.title";
    public static final String DISH_CARD_MACROS = "dish.card.macros";
    public static final String DISH_CARD_PORTION_WEIGHT = "dish.card.portion-weight";
    public static final String DISH_CARD_PORTIONS = "dish.card.portions";
    public static final String DISH_CARD_CALORIES_PROGRESS = "dish.card.calories-progress";
    public static final String DISH_CARD_TODAY_TOTAL = "dish.card.today-total";
    public static final String DISH_BUTTON_PORTION_WEIGHT = "dish.button.portion-weight";
    public static final String DISH_BUTTON_PORTIONS = "dish.button.portions";
    public static final String DISH_BUTTON_SAVE_FAVORITE = "dish.button.save-favorite";
    public static final String DISH_BUTTON_IN_FAVORITES = "dish.button.in-favorites";
    public static final String DISH_BUTTON_DELETE = "dish.button.delete";
    public static final String DISH_BUTTON_NOT_COUNTED = "dish.button.not-counted";

    // ── Распознавание блюда ────────────────────────────────────────
    public static final String DISH_NOT_FOOD = "dish.not-food";
    public static final String DISH_UNSUPPORTED_MESSAGE = "dish.unsupported-message";
    public static final String DISH_VOICE_ERROR = "dish.voice.error";
    public static final String DISH_VOICE_TOO_LONG = "dish.voice.too-long";
    public static final String DISH_PHOTO_PROVIDER_ERROR = "dish.photo.provider-error";
    public static final String DISH_PHOTO_NOT_RECOGNIZED_NOT_COUNTED = "dish.photo.not-recognized.not-counted";
    public static final String DISH_PHOTO_NOT_RECOGNIZED_COUNTED = "dish.photo.not-recognized.counted";
    public static final String DISH_SCALE_ERROR = "dish.scale.error";
    public static final String DISH_CHOICE_ERROR = "dish.choice.error";

    // ── Общее ──────────────────────────────────────────────────────
    public static final String COMMON_BUTTON_BACK = "common.button.back";
    public static final String COMMON_ERROR = "common.error";
    public static final String COMMON_UNIT_KCAL = "common.unit.kcal";
    public static final String COMMON_UNIT_GRAM = "common.unit.gram";

    // ── Подписи КБЖУ ───────────────────────────────────────────────
    public static final String MACRO_LABEL_CALORIES = "macro.label.calories";
    public static final String MACRO_LABEL_PROTEINS = "macro.label.proteins";
    public static final String MACRO_LABEL_FATS = "macro.label.fats";
    public static final String MACRO_LABEL_CARBS = "macro.label.carbs";
    public static final String MACRO_LABEL_WATER = "macro.label.water";

    // ── «Сегодня» ──────────────────────────────────────────────────
    public static final String TODAY_DIARY_TITLE = "today.diary-title";
    public static final String TODAY_DISH_NO_DATA = "today.dish.no-data";
    public static final String TODAY_DISH_NO_NAME = "today.dish.no-name";
    public static final String TODAY_DISH_MACROS = "today.dish.macros";
    public static final String TODAY_DISH_WEIGHT = "today.dish.weight";
    public static final String TODAY_DISH_PORTIONS = "today.dish.portions";
    public static final String TODAY_DISH_AI_CONFIDENCE = "today.dish.ai-confidence";
    public static final String TODAY_TOTAL_TITLE = "today.total-title";
    public static final String TODAY_BUTTON_GO_TO_REMOVE = "today.button.go-to-remove";

    // ── Неделя ─────────────────────────────────────────────────────
    public static final String WEEK_TITLE = "week.title";
    public static final String WEEK_DISH_MACROS = "week.dish-macros";
    public static final String WEEK_DAY_TOTAL = "week.day-total";
    public static final String WEEK_DAY_CALORIES = "week.day-calories";
    public static final String WEEK_DAY_MACROS = "week.day-macros";
    public static final String WEEK_SUMMARY_TITLE = "week.summary-title";
    public static final String WEEK_DISCIPLINE = "week.discipline";
    public static final String WEEK_AVERAGE = "week.average";
    public static final String WEEK_BALANCE = "week.balance";
    public static final String WEEK_VERDICT = "week.verdict";
    public static final String WEEK_VERDICT_NO_DATA = "week.verdict.no-data";
    public static final String WEEK_VERDICT_LOW_PROTEIN = "week.verdict.low-protein";
    public static final String WEEK_VERDICT_HIGH_PROTEIN = "week.verdict.high-protein";
    public static final String WEEK_VERDICT_BALANCED = "week.verdict.balanced";

    // ── Меню аналитики ─────────────────────────────────────────────
    public static final String REPORT_CHOOSE = "report.choose";
    public static final String REPORT_DATA_ERROR = "report.data-error";
    public static final String REPORT_BUTTON_DAY = "report.button.day";
    public static final String REPORT_BUTTON_WEEK = "report.button.week";
    public static final String REPORT_BUTTON_MONTH = "report.button.month";
    public static final String REPORT_BUTTON_WEIGHT = "report.button.weight";

    // ── История веса ───────────────────────────────────────────────
    public static final String WEIGHT_HISTORY_EMPTY = "weight.history.empty";
    public static final String WEIGHT_HISTORY_TITLE = "weight.history.title";
    public static final String WEIGHT_HISTORY_ENTRY = "weight.history.entry";
    public static final String WEIGHT_HISTORY_TOTAL = "weight.history.total";

    // ── Рассылки ──────────────────────────────────────────────────
    public static final String REMINDER_MEAL = "reminder.meal";
}
