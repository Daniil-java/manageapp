package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Command {
    //==========================CALORIE============================
    CALORIE_START("/start", BotIdentifier.CALORIE_BOT),
    CALORIE_DELETE("/deleteDish", BotIdentifier.CALORIE_BOT),
    CALORIE_GENERAL("/general", BotIdentifier.CALORIE_BOT),
    CALORIE_TODAY_LIST("\uD83D\uDCCA СЕГОДНЯ", BotIdentifier.CALORIE_BOT),
    CALORIE_WEEK_LIST("\uD83D\uDCC5 НЕДЕЛЯ", BotIdentifier.CALORIE_BOT),
    CALORIE_CHOICE("/choice", BotIdentifier.CALORIE_BOT),


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


    //==========================HH============================
    HH_DECISION("/decision", BotIdentifier.HH_BOT),


    //==========================AVIA============================
    AVIA_START("/start", BotIdentifier.AVIA_BOT),
    AVIA_FLIGHT("/flight", BotIdentifier.AVIA_BOT),
    AVIA_BOARD("/board", BotIdentifier.AVIA_BOT),
    AVIA_SUBSCRIBE("subscribe", BotIdentifier.AVIA_BOT),
    AVIA_UNSUBSCRIBE("unsubscribe", BotIdentifier.AVIA_BOT),
    AVIA_ERROR("error", BotIdentifier.AVIA_BOT),


    //==========================METRICS============================
    METRICS_GET("/get", BotIdentifier.METRICS),
    METRICS_TEST_ERROR("/test", BotIdentifier.METRICS)
    ;

    private final String commandText;
    private final BotIdentifier botIdentifier;

}
