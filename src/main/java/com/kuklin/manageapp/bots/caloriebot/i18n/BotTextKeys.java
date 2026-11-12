package com.kuklin.manageapp.bots.caloriebot.i18n;

import com.kuklin.manageapp.bots.caloriebot.support.SupportTicket;
import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.UserSubscription;

/**
 * Ключи текстов бота — словари i18n/calorie/bot_<code>.properties.
 * В коде передаём только эти константы, не строки: опечатку в ключе поймает TextBundlesConsistencyTest.
 */
public final class BotTextKeys {
    private BotTextKeys() {
    }

    /** Подпись статуса подписки: subscription.status.ACTIVE… (подписи в самом enum модуля оплаты русские). */
    public static String buildSubscriptionStatusKey(UserSubscription.Status status) {
        return "subscription.status." + status.name();
    }

    /** Сообщение модуля оплаты: payment.text.PRE_CHECKOUT_ERROR… (PaymentModuleTexts). */
    public static String buildPaymentTextKey(PaymentText text) {
        return "payment.text." + text.name();
    }

    /** Способ оплаты на кнопке выбора: payment.provider.YOOKASSA… */
    public static String buildPaymentProviderKey(Payment.Provider provider) {
        return "payment.provider." + provider.name();
    }

    /** Тема заявки в поддержку на кнопке: support.category.PAYMENT… (и в карточке заявки в группе — по-русски). */
    public static String buildSupportCategoryKey(SupportTicket.Category category) {
        return "support.category." + category.name();
    }

    /** Что написать в заявку этой темы: support.prompt.PAYMENT… */
    public static String buildSupportPromptKey(SupportTicket.Category category) {
        return "support.prompt." + category.name();
    }

    // ── Выбор языка: /start, ⚙ Настройки → 🌐 Язык, /lang ─────────
    /** Блок вопроса о языке в /start — на каждом языке свой, вопрос собирается из всех. {0} — флаг. */
    public static final String LANGUAGE_CHOOSE_PROMPT = "language.choose-prompt";
    public static final String LANGUAGE_CHOSEN = "language.chosen";
    public static final String LANGUAGE_SETTINGS_TITLE = "language.settings-title";
    public static final String LANGUAGE_CHANGED = "language.changed";

    // ── /lang <код>, /lang reset (только для разработчиков) ────────
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
    public static final String BUTTON_MENU = "button.menu";

    // ── Общие подписи кнопок ───────────────────────────────────────
    public static final String COMMON_BUTTON_CLOSE = "common.button.close";
    public static final String COMMON_BUTTON_TO_MENU = "common.button.to-menu";
    public static final String COMMON_BUTTON_NEXT = "common.button.next";
    public static final String COMMON_BUTTON_INSTRUCTION = "common.button.instruction";
    public static final String COMMON_ALREADY_IN_PROCESS = "common.already-in-process";
    public static final String COMMON_OVERLOADED = "common.overloaded";

    // ── Онбординг: /start, знакомство, FAQ, /menu, /keyboard, /support ──
    public static final String START_WELCOME = "start.welcome";
    public static final String START_WELCOME_TRIAL = "start.welcome.trial";
    public static final String START_LEGAL = "start.legal";
    public static final String START_LANGUAGE_AUTO = "start.language-auto";
    public static final String START_FIRST_DISH_CALL = "start.first-dish-call";
    public static final String START_BUTTON_SAMPLE = "start.button.sample";
    public static final String START_BUTTON_MORE = "start.button.more";
    public static final String START_SAMPLE_DISH_NAME = "start.sample.dish-name";
    public static final String START_SAMPLE_AFTER = "start.sample.after";
    public static final String START_SAMPLE_AFTER_PROFILE_FILLED = "start.sample.after.profile-filled";
    public static final String START_SAMPLE_HINT_DELETE = "start.sample.hint.delete";
    public static final String START_SAMPLE_HINT_FAVORITE = "start.sample.hint.favorite";
    public static final String START_CHANNEL = "start.channel";
    public static final String START_FIRST_DISH = "start.first-dish";
    public static final String START_FIRST_DISH_PROFILE = "start.first-dish.profile";
    public static final String START_FLOW_BUTTON_FILL_PROFILE = "start.flow.button.fill-profile";
    public static final String INTRO_HOME = "intro.home";
    public static final String INTRO_BUTTON_FOOD = "intro.button.food";
    public static final String INTRO_BUTTON_PROFILE = "intro.button.profile";
    public static final String INTRO_BUTTON_REMINDERS = "intro.button.reminders";
    public static final String INTRO_BUTTON_REPORTS = "intro.button.reports";
    public static final String INTRO_BUTTON_PREMIUM = "intro.button.premium";
    public static final String INTRO_BUTTON_REWARDS = "intro.button.rewards";
    public static final String INTRO_BUTTON_CHANNEL = "intro.button.channel";
    public static final String INTRO_BUTTON_FAQ = "intro.button.faq";
    public static final String INTRO_BUTTON_BACK = "intro.button.back";
    public static final String INTRO_BUTTON_OPEN_CHANNEL = "intro.button.open-channel";
    public static final String INTRO_FOOD = "intro.food";
    public static final String INTRO_PROFILE_EMPTY = "intro.profile.empty";
    public static final String INTRO_PROFILE_FILLED = "intro.profile.filled";
    public static final String INTRO_REMINDERS = "intro.reminders";
    public static final String INTRO_REPORTS = "intro.reports";
    public static final String WELCOME_TEXT = "welcome.text";
    public static final String MENU_TEXT = "menu.text";
    public static final String KEYBOARD_UPDATED = "keyboard.updated";
    public static final String KEYBOARD_PLACEHOLDER = "keyboard.placeholder";
    public static final String HELP_TEXT = "help.text";
    public static final String HELP_BUTTON_INTRO = "help.button.intro";

    // Описания команд в списке «/» (CalorieBotCommandsPublisher)
    public static final String COMMAND_MENU = "command.menu";
    public static final String COMMAND_HELP = "command.help";
    public static final String COMMAND_INTRO = "command.intro";
    public static final String COMMAND_KEYBOARD = "command.keyboard";
    public static final String COMMAND_LANGUAGE = "command.lang";
    public static final String COMMAND_SUPPORT = "command.support";
    public static final String COMMAND_TERMS = "command.terms";
    public static final String COMMAND_PRIVACY = "command.privacy";
    public static final String SUPPORT_TEXT = "support.text";
    public static final String SUPPORT_BUTTON = "support.button";

    // ── Заявки в поддержку: /support, /paysupport (группа поддержки настроена) ──
    public static final String SUPPORT_CHOOSE = "support.choose";
    public static final String SUPPORT_PROMPT_APPEND = "support.prompt.append";
    public static final String SUPPORT_BUTTON_APPEND = "support.button.append";
    public static final String SUPPORT_BUTTON_CANCEL = "support.button.cancel";
    public static final String SUPPORT_CANCELLED = "support.cancelled";
    public static final String SUPPORT_TICKET_CREATED = "support.ticket-created";
    public static final String SUPPORT_MESSAGE_SENT = "support.message-sent";
    public static final String SUPPORT_UNSUPPORTED_CONTENT = "support.unsupported-content";
    public static final String SUPPORT_TICKET_CLOSED = "support.ticket-closed";
    public static final String SUPPORT_REPLY_HEADER = "support.reply-header";
    public static final String SUPPORT_BUTTON_REPLY = "support.button.reply";
    public static final String SUPPORT_CLOSED_NOTICE = "support.closed-notice";
    public static final String SUPPORT_BUTTON_RATE_UP = "support.button.rate-up";
    public static final String SUPPORT_BUTTON_RATE_DOWN = "support.button.rate-down";
    public static final String SUPPORT_RATED = "support.rated";

    // ── /privacy, /terms: сами тексты — на сайте ───────────────────
    public static final String LEGAL_PRIVACY_TEXT = "legal.privacy.text";
    public static final String LEGAL_TERMS_TEXT = "legal.terms.text";
    public static final String LEGAL_BUTTON_OPEN = "legal.button.open";

    // ── Реферальная программа и UTM-ссылки ─────────────────────────
    public static final String REFERRAL_TEXT = "referral.text";
    public static final String REFERRAL_SHARE_TEXT = "referral.share-text";
    public static final String REFERRAL_BUTTON_SHARE = "referral.button.share";
    public static final String REFERRAL_REWARD_INVITED = "referral.reward.invited";
    public static final String REFERRAL_REWARD_INVITER = "referral.reward.inviter";
    public static final String REFERRAL_REWARD_INVITER_LIMIT = "referral.reward.inviter-limit";
    public static final String REFERRAL_REWARD_FRIEND_PAID = "referral.reward.friend-paid";
    public static final String REFERRAL_REWARD_FRIEND_PAID_REVOKED = "referral.reward.friend-paid-revoked";
    // Бонус по партнёрской ссылке на /start (StartUpdateHandler)
    public static final String START_PARTNER_BONUS = "start.partner-bonus";
    // Предложение пригласить друга: в старт-флоу и после вечернего отчёта (InvitePromptService)
    public static final String INVITE_START_FLOW = "invite.start-flow";
    public static final String INVITE_PROMPT = "invite.prompt";
    public static final String INVITE_BUTTON_DETAILS = "invite.button.details";
    // Карточка «мой день» (DayCardService, DayCardCalorieUpdateHandler)
    public static final String CARD_BUTTON = "card.button";
    public static final String CARD_CAPTION = "card.caption";
    public static final String CARD_EMPTY = "card.empty";
    public static final String CARD_FAILED = "card.failed";
    public static final String CARD_ALREADY_SENT = "card.already-sent";
    public static final String CARD_TITLE = "card.title";
    public static final String CARD_OF_NORM = "card.of-norm";
    public static final String CARD_NO_NORM = "card.no-norm";
    public static final String CARD_STREAK = "card.streak";
    public static final String CARD_DISHES = "card.dishes";
    public static final String CARD_FOOTER = "card.footer";
    public static final String CARD_FOOTER_BONUS = "card.footer-bonus";
    public static final String ADMIN_UTM_BUTTON_CREATE = "admin.utm.button.create";
    public static final String ADMIN_UTM_CREATED = "admin.utm.created";
    public static final String ADMIN_UTM_CODE_INVALID = "admin.utm.code-invalid";
    public static final String ADMIN_UTM_CODE_RESERVED = "admin.utm.code-reserved";
    public static final String ADMIN_UTM_CODE_TAKEN = "admin.utm.code-taken";

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
    public static final String DISH_BUTTON_WEIGHT_VALUE = "dish.button.weight-value";
    public static final String DISH_BUTTON_PORTIONS_VALUE = "dish.button.portions-value";
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
    public static final String TODAY_BUTTON_TODAY = "today.button.today";

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

    // ── Общие кнопки и единицы ─────────────────────────────────────
    public static final String COMMON_BUTTON_SAVE = "common.button.save";
    public static final String COMMON_BUTTON_NO = "common.button.no";
    public static final String COMMON_BUTTON_ON = "common.button.on";
    public static final String COMMON_BUTTON_OFF = "common.button.off";
    public static final String COMMON_UNIT_ML = "common.unit.ml";
    public static final String COMMON_UNIT_CM = "common.unit.cm";
    public static final String COMMON_UNIT_KG = "common.unit.kg";
    public static final String COMMON_DURATION_HOURS = "common.duration.hours";

    // ── Профиль питания: просмотр ──────────────────────────────────
    // Значения пола, активности, цели, диеты — UserNutritionProfile.*.getTextKey(); пол с иконкой — profile.view.sex.<SEX>
    public static final String PROFILE_VIEW_TITLE = "profile.view.title";
    public static final String PROFILE_VIEW_SEX = "profile.view.sex";
    public static final String PROFILE_VIEW_AGE = "profile.view.age";
    public static final String PROFILE_VIEW_AGE_VALUE = "profile.view.age-value";
    public static final String PROFILE_VIEW_HEIGHT = "profile.view.height";
    public static final String PROFILE_VIEW_WEIGHT = "profile.view.weight";
    public static final String PROFILE_VIEW_ACTIVITY = "profile.view.activity";
    public static final String PROFILE_VIEW_GOAL = "profile.view.goal";
    public static final String PROFILE_VIEW_DIET = "profile.view.diet";
    public static final String PROFILE_VIEW_NO_DATA = "profile.view.no-data";
    public static final String PROFILE_VIEW_NORMS_TITLE = "profile.view.norms-title";
    public static final String PROFILE_VIEW_NORMS_NO_DATA = "profile.view.norms-no-data";
    public static final String PROFILE_VIEW_CALORIES = "profile.view.calories";
    public static final String PROFILE_VIEW_PROTEINS = "profile.view.proteins";
    public static final String PROFILE_VIEW_FATS = "profile.view.fats";
    public static final String PROFILE_VIEW_CARBS = "profile.view.carbs";
    public static final String PROFILE_VIEW_MANUAL_MARK = "profile.view.manual-mark";
    public static final String PROFILE_VIEW_MODE_MANUAL = "profile.view.mode.manual";
    public static final String PROFILE_VIEW_MODE_AUTO = "profile.view.mode.auto";
    public static final String PROFILE_VIEW_WATER_TITLE = "profile.view.water-title";
    public static final String PROFILE_VIEW_WATER_NO_DATA = "profile.view.water-no-data";
    public static final String PROFILE_VIEW_WATER = "profile.view.water";

    // ── Профиль питания: экран, редактирование, поля ───────────────
    // Подписи действий — ProfileEditAction.getTextKey(), значение на счётчике — getValueKey(), заголовок выбора — getChooseKey()
    public static final String PROFILE_EMPTY = "profile.empty";
    public static final String PROFILE_BUTTON_FILL = "profile.button.fill";
    public static final String PROFILE_BUTTON_EDIT = "profile.button.edit";
    public static final String PROFILE_BUTTON_BACK = "profile.button.back";
    public static final String PROFILE_EDIT_QUESTION = "profile.edit.question";
    public static final String PROFILE_EDIT_BUTTON_AUTO = "profile.edit.button.auto";
    public static final String PROFILE_EDIT_BUTTON_MANUAL = "profile.edit.button.manual";
    public static final String PROFILE_VALIDATION_ERROR = "profile.validation-error";
    public static final String PROFILE_FIELD_AGE = "profile.field.age";
    public static final String PROFILE_FIELD_AGE_EMPTY = "profile.field.age-empty";
    public static final String PROFILE_FIELD_HEIGHT = "profile.field.height";
    public static final String PROFILE_FIELD_HEIGHT_EMPTY = "profile.field.height-empty";
    public static final String PROFILE_FIELD_WEIGHT = "profile.field.weight";
    public static final String PROFILE_FIELD_WEIGHT_EMPTY = "profile.field.weight-empty";
    public static final String PROFILE_FIELD_CALORIES = "profile.field.calories";
    public static final String PROFILE_FIELD_CALORIES_EMPTY = "profile.field.calories-empty";
    public static final String PROFILE_FIELD_CALORIES_FORMULA = "profile.field.calories-formula";
    public static final String PROFILE_FIELD_CALORIES_HINT = "profile.field.calories-hint";
    public static final String PROFILE_FIELD_WATER = "profile.field.water";
    public static final String PROFILE_FIELD_WATER_EMPTY = "profile.field.water-empty";
    public static final String PROFILE_FIELD_WATER_FORMULA = "profile.field.water-formula";
    public static final String PROFILE_FIELD_WATER_HINT_MANUAL = "profile.field.water-hint.manual";
    public static final String PROFILE_FIELD_WATER_HINT_MANUAL_FORMULA = "profile.field.water-hint.manual-formula";
    public static final String PROFILE_FIELD_WATER_HINT_AUTO = "profile.field.water-hint.auto";
    public static final String PROFILE_FIELD_MARK_MANUAL = "profile.field.mark.manual";
    public static final String PROFILE_FIELD_MARK_FORMULA = "profile.field.mark.formula";
    public static final String PROFILE_FIELD_BUTTON_WATER_FORMULA = "profile.field.button.water-formula";

    // ── Пересчёт норм ──────────────────────────────────────────────
    public static final String PROFILE_RECALC_NEEDS_PROFILE = "profile.recalc.needs-profile";
    public static final String PROFILE_RECALC_WATER_NEEDS_PROFILE = "profile.recalc.water-needs-profile";
    public static final String PROFILE_RECALC_ASK_AUTO = "profile.recalc.ask-auto";
    public static final String PROFILE_RECALC_ASK_WATER = "profile.recalc.ask-water";
    public static final String PROFILE_RECALC_DIFF_LINE = "profile.recalc.diff-line";
    public static final String PROFILE_RECALC_BUTTON_YES = "profile.recalc.button.yes";
    public static final String PROFILE_RECALC_BUTTON_YES_WATER = "profile.recalc.button.yes-water";
    public static final String PROFILE_LABEL_CALORIES = "profile.label.calories";
    public static final String PROFILE_LABEL_PROTEINS = "profile.label.proteins";
    public static final String PROFILE_LABEL_FATS = "profile.label.fats";
    public static final String PROFILE_LABEL_CARBS = "profile.label.carbs";
    public static final String PROFILE_LABEL_WATER = "profile.label.water";

    // ── Калькулятор с лендинга ─────────────────────────────────────
    public static final String CALC_APPLIED = "calc.applied";
    public static final String CALC_SAME = "calc.same";
    public static final String CALC_SKIPPED = "calc.skipped";
    public static final String CALC_BROKEN = "calc.broken";
    public static final String CALC_UPDATED = "calc.updated";
    public static final String CALC_QUESTION_NORM = "calc.question.norm";
    public static final String CALC_QUESTION_NORM_UNKNOWN = "calc.question.norm-unknown";
    public static final String CALC_QUESTION_DATA = "calc.question.data";
    public static final String CALC_QUESTION_MANUAL_NORM = "calc.question.manual-norm";
    public static final String CALC_QUESTION_ASK = "calc.question.ask";
    public static final String CALC_NORM = "calc.norm";
    public static final String CALC_BUTTON_UPDATE = "calc.button.update";
    public static final String CALC_BUTTON_KEEP = "calc.button.keep";

    // ── Настройки ──────────────────────────────────────────────────
    public static final String SETTINGS_REMINDERS_ON = "settings.reminders-on";
    public static final String SETTINGS_REMINDERS_OFF = "settings.reminders-off";
    public static final String SETTINGS_TIMEZONE = "settings.timezone";
    public static final String SETTINGS_LANGUAGE = "settings.language";
    public static final String SETTINGS_DAILY_ON = "settings.daily-on";
    public static final String SETTINGS_DAILY_OFF = "settings.daily-off";
    public static final String SETTINGS_DAILY_TIME = "settings.daily-time";
    public static final String SETTINGS_DAILY_TIME_LOCAL = "settings.daily-time-local";
    public static final String SETTINGS_MEAL_ON = "settings.meal-on";
    public static final String SETTINGS_MEAL_OFF = "settings.meal-off";
    public static final String SETTINGS_MEAL_INTERVAL = "settings.meal-interval";
    public static final String SETTINGS_BUTTON_TIMEZONE = "settings.button.timezone";
    public static final String SETTINGS_BUTTON_REMINDERS = "settings.button.reminders";
    public static final String SETTINGS_BUTTON_LANGUAGE = "settings.button.language";
    public static final String SETTINGS_BUTTON_DAILY = "settings.button.daily";
    public static final String SETTINGS_BUTTON_MEAL = "settings.button.meal";
    public static final String SETTINGS_BUTTON_BACK_TO_SETTINGS = "settings.button.back-to-settings";
    public static final String SETTINGS_TIMEZONE_SET = "settings.timezone-set";

    // ── Общее ──────────────────────────────────────────────────────
    public static final String COMMON_BUTTON_YES = "common.button.yes";
    public static final String COMMON_DATA_ERROR = "common.data-error";

    // ── Лимиты (FeatureLimitNotifier). Название функции — BotFeature.getTextKey() ──
    public static final String LIMIT_FEATURE = "limit.feature";
    public static final String LIMIT_FREE_ONLY_SUBSCRIPTION = "limit.free.only-subscription";
    public static final String LIMIT_FREE_DAILY = "limit.free.daily";
    public static final String LIMIT_FREE_MONTHLY = "limit.free.monthly";
    public static final String LIMIT_FREE_LIFETIME = "limit.free.lifetime";
    public static final String LIMIT_FREE_UNLIMITED = "limit.free.unlimited";
    public static final String LIMIT_AI_SUBSCRIPTION_HINT = "limit.ai.subscription-hint";

    // ── Избранное ──────────────────────────────────────────────────
    public static final String FAVORITES_PAGE_TITLE = "favorites.page-title";
    public static final String FAVORITES_DELETE_PAGE_TITLE = "favorites.delete.page-title";
    public static final String FAVORITES_EMPTY_HINT = "favorites.empty-hint";
    public static final String FAVORITES_EMPTY = "favorites.empty";
    public static final String FAVORITES_ADD_FAILED = "favorites.add-failed";
    public static final String FAVORITES_SAVED = "favorites.saved";
    public static final String FAVORITES_SAVE_FAILED = "favorites.save-failed";
    public static final String FAVORITES_DELETE_CONFIRM = "favorites.delete.confirm";
    public static final String FAVORITES_BUTTON_DELETE_MODE = "favorites.button.delete-mode";
    public static final String FAVORITES_BUTTON_BACK_TO_ADD = "favorites.button.back-to-add";

    // ── Вода ───────────────────────────────────────────────────────
    public static final String WATER_STATUS = "water.status";
    public static final String WATER_BUTTON_UNDO = "water.button.undo";

    // ── Подписка. Статус — subscription.status.<UserSubscription.Status> ──
    public static final String PAYMENT_PREMIUM_EXPIRED = "payment.premium-expired";
    public static final String PAYMENT_PLANS = "payment.plans";
    public static final String PAYMENT_IMAGE_ERROR = "payment.image-error";
    public static final String PAYMENT_SUBSCRIPTION_STARTED = "payment.subscription-started";
    public static final String PAYMENT_PLANS_CAPTION = "payment.plans.caption";
    public static final String PAYMENT_PLAN_BUTTON = "payment.plan-button";
    public static final String PAYMENT_PLAN_BUTTON_SAVING = "payment.plan-button.saving";
    public static final String PAYMENT_DURATION_MONTHS = "payment.duration.months";
    public static final String PAYMENT_DURATION_DAYS = "payment.duration.days";
    public static final String PAYMENT_BUTTON_PREMIUM_FROM = "payment.button.premium-from";
    public static final String PAYMENT_BUTTON_EXTEND_FROM = "payment.button.extend-from";
    public static final String PAYMENT_BUTTON_PLANS = "payment.button.plans";
    public static final String PAYMENT_BUTTON_FREE_INVITE = "payment.button.free-invite";
    public static final String PREMIUM_STATUS_FREE = "premium.status.free";
    public static final String PREMIUM_STATUS_ACTIVE = "premium.status.active";
    public static final String PREMIUM_LEFT_DAYS = "premium.left.days";
    public static final String PREMIUM_LEFT_LESS_THAN_DAY = "premium.left.less-than-day";
    public static final String PREMIUM_STATUS_PARTS = "premium.status.parts";
    public static final String PREMIUM_STATUS_PART = "premium.status.part";
    public static final String PREMIUM_DATE_TIME = "premium.date-time";
    public static final String PREMIUM_ENDING = "premium.ending";

    // ── Рассылки ──────────────────────────────────────────────────
    public static final String REMINDER_MEAL = "reminder.meal";
    public static final String REMINDER_FIRST_DISH_FIRST = "reminder.first-dish.first";
    public static final String REMINDER_FIRST_DISH_SECOND = "reminder.first-dish.second";
}
