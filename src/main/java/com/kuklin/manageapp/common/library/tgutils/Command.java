package com.kuklin.manageapp.common.library.tgutils;

import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Command {
    //==========================CALORIE============================
    CALORIE_START("/start", BotIdentifier.CALORIE_BOT),
    CALORIE_START_FLOW_1("/start1", BotIdentifier.CALORIE_BOT),
    CALORIE_DELETE("/deleteDish", BotIdentifier.CALORIE_BOT),
    CALORIE_GENERAL("/general", BotIdentifier.CALORIE_BOT),
    CALORIE_TODAY_LIST("\uD83D\uDCCAСЕГОДНЯ", BotIdentifier.CALORIE_BOT),
    CALORIE_WEEK_LIST("\uD83D\uDCC5 НЕДЕЛЯ", BotIdentifier.CALORIE_BOT),
    CALORIE_CHOICE("/choice", BotIdentifier.CALORIE_BOT),
    CALORIE_SCALE("/scale", BotIdentifier.CALORIE_BOT),
    CALORIE_FAVORITE("\uD83C\uDF1F ИЗБРАННЫЕ БЛЮДА", BotIdentifier.CALORIE_BOT),
    CALORIE_FAVORITE_ADD("/favoriteadd", BotIdentifier.CALORIE_BOT),
    CALORIE_FAVORITE_DELETE("/favoritedel", BotIdentifier.CALORIE_BOT),
    CALORIE_CLOSE("/close", BotIdentifier.CALORIE_BOT),
    CALORIE_PROFILE("\uD83D\uDC64Мой профиль", BotIdentifier.CALORIE_BOT),
    CALORIE_PROFILE_EDIT("/profileedit", BotIdentifier.CALORIE_BOT),
    CALORIE_PROFILE_DIALOGUE("/profiledialogue", BotIdentifier.CALORIE_BOT),
    CALORIE_WATER("💧", BotIdentifier.CALORIE_BOT),
    CALORIE_WEIGHT("Вес", BotIdentifier.CALORIE_BOT),
    CALORIE_WEIGHT_HISTORY("⚖ВЕС", BotIdentifier.CALORIE_BOT),
    CALORIE_STATS("КБЖУ", BotIdentifier.CALORIE_BOT),
    CALORIE_SETTINGS("⚙Настройки", BotIdentifier.CALORIE_BOT),
    CALORIE_SETTINGS_TIMEZONE("tzset", BotIdentifier.CALORIE_BOT),
    CALORIE_SETTINGS_REMINDERS("remind", BotIdentifier.CALORIE_BOT),
    CALORIE_ADMIN_MESSAGE("/admin", BotIdentifier.CALORIE_BOT),
    CALORIE_REPORT("\uD83D\uDCC4АНАЛИТИКА", BotIdentifier.CALORIE_BOT),
    CALORIE_REMOVE_LIST("/remove", BotIdentifier.CALORIE_BOT),
    CALORIE_WELCOME("📖FAQ", BotIdentifier.CALORIE_BOT),
    CALORIE_PAYMENT_PAYLOAD_PLAN("\uD83D\uDCB3ПОДПИСКА", BotIdentifier.CALORIE_BOT),
    CALORIE_SUB_STATUS("\uD83D\uDD0DSTATUS", BotIdentifier.CALORIE_BOT),
    CALORIE_MENU("/menu", BotIdentifier.CALORIE_BOT),
    CALORIE_UTM("\uD83D\uDC8EПригласить", BotIdentifier.CALORIE_BOT),
    CALORIE_SUPPORT("/support", BotIdentifier.CALORIE_BOT),
    CALORIE_KEYBOARD("/keyboard", BotIdentifier.CALORIE_BOT),
    CALORIE_TERMS("/terms", BotIdentifier.CALORIE_BOT),
    CALORIE_PRIVACY("/privacy", BotIdentifier.CALORIE_BOT),

    //==========================PAYMENT============================
    PAYMENT_PRE_CHECK_QUERY("precheckquerynotcommand", BotIdentifier.PAYMENT),
    PAYMENT_PAYLOAD_PLAN("/plan", BotIdentifier.PAYMENT),
    PAYMENT_PAYLOAD_PLAN_CHOICE_PROVIDER("/choiceprovider", BotIdentifier.PAYMENT),
    PAYMENT_PLAN("/payplan", BotIdentifier.PAYMENT),
    PAYMENT_YOOKASSA_URL_CREATE("/createurl", BotIdentifier.PAYMENT),
    PAYMENT_SUCCESS("paymentsuccesnotcommand", BotIdentifier.PAYMENT),
    PAYMENT_BALANCE("/balance", BotIdentifier.PAYMENT),
    PAYMENT_PROVIDER("/provider", BotIdentifier.PAYMENT),
    PAYMENT_REFUND("/refund", BotIdentifier.PAYMENT),
    PAYMENT_NOT_COMMAND("notcommand", BotIdentifier.PAYMENT),


    //==========================ASSISTNANT============================
    ASSISTANT_START("/start", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_VOICE("voicenotcommand", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_TODAY("/today", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_SET_CALENDARID("/set", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_DELETE("/delete", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_HELP("/help", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_AUTH("/auth", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_AUTH_STATUS("/auth_status", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_CHOOSE_CALENDAR("/choosecalendar", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_TABLE("/table", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_TZ("/tz", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_DAILY_TIME("/notify_time", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_GET_CALENDAR("/getcalendar", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_SETTINGS("/settings", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_CLOSE("/close", BotIdentifier.ASSISTANT_BOT),


    //==========================KWORK============================
    KWORK_START("/start", BotIdentifier.KWORK),
    KWORK_URL("/url", BotIdentifier.KWORK),


    //==========================BOOKING============================
    BOOKING_START("/start", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOKINGOBJECT("📅 Забронировать", BotIdentifier.BOOKING_BOT),
    BOOKING_CALENDAR("/calendar", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOKTIME("/booktime", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOK("/book", BotIdentifier.BOOKING_BOT),
    BOOKING_FORM("/form", BotIdentifier.BOOKING_BOT),
    BOOKING_MYLIST("\uD83D\uDCDA Моя бронь", BotIdentifier.BOOKING_BOT),
    BOOKING_DELETE_BOOKING("/deletebook", BotIdentifier.BOOKING_BOT),
    BOOKING_NULL("null", BotIdentifier.BOOKING_BOT),
    BOOKING_YANDEX_TEST("/yandex", BotIdentifier.BOOKING_BOT),
    BOOKING_MENU("/menu", BotIdentifier.BOOKING_BOT),
    //==========================HH============================
    HH_DECISION("/decision", BotIdentifier.HH_BOT),


    //==========================AVIA============================
    AVIA_START("/start", BotIdentifier.AVIA_BOT),
    AVIA_FLIGHT("/flight", BotIdentifier.AVIA_BOT),
    AVIA_BOARD("/board", BotIdentifier.AVIA_BOT),
    AVIA_SUBSCRIBE("subscribe", BotIdentifier.AVIA_BOT),
    AVIA_UNSUBSCRIBE("unsubscribe", BotIdentifier.AVIA_BOT),
    AVIA_ERROR("error", BotIdentifier.AVIA_BOT),

    //==========================POSTER============================
    POSTER_GET_ARTICLE("/article", BotIdentifier.CHANNEL_POSTER),
    POSTER_IMAGE("/image", BotIdentifier.CHANNEL_POSTER),
    POSTER_POST("/post", BotIdentifier.CHANNEL_POSTER),
    POSTER_MINI_APP("/app", BotIdentifier.CHANNEL_POSTER),
    POSTER_SCHEDULE_ARTICLE("/artsch", BotIdentifier.CHANNEL_POSTER),
    POSTER_GET_QUEUE("/q", BotIdentifier.CHANNEL_POSTER),

    //==========================METRICS============================
    METRICS_GET("/get", BotIdentifier.METRICS),
    METRICS_TEST_ERROR("/test", BotIdentifier.METRICS),
    ADMIN_SEND_MSG("$@#dummy", BotIdentifier.PAYMENT);
    private final String commandText;
    private final BotIdentifier botIdentifier;

}
