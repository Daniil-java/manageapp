package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Command {
    CALORIE_START("/start", BotIdentifier.CALORIE_BOT),
    CALORIE_DELETE("/deleteDish", BotIdentifier.CALORIE_BOT),
    CALORIE_GENERAL("/general", BotIdentifier.CALORIE_BOT),
    CALORIE_TODAY_LIST("\uD83D\uDCCA СЕГОДНЯ", BotIdentifier.CALORIE_BOT),
    CALORIE_WEEK_LIST("\uD83D\uDCC5 НЕДЕЛЯ", BotIdentifier.CALORIE_BOT),
    ASSISTANT_START("/start", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_VOICE("voice", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_TODAY("/today", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_SET_CALENDARID("/set", BotIdentifier.ASSISTANT_BOT),
    ASSISTANT_DELETE("/delete", BotIdentifier.ASSISTANT_BOT),
    KWORK_START("/start", BotIdentifier.KWORK),
    KWORK_URL("/url", BotIdentifier.KWORK),
    BOOKING_START("/start", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOKINGOBJECT("📅 Забронировать", BotIdentifier.BOOKING_BOT),
    BOOKING_CALENDAR("/calendar", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOKTIME("/booktime", BotIdentifier.BOOKING_BOT),
    BOOKING_BOOK("/book", BotIdentifier.BOOKING_BOT),
    BOOKING_FORM("/form", BotIdentifier.BOOKING_BOT),
    BOOKING_MYLIST("\uD83D\uDCDA Моя бронь", BotIdentifier.BOOKING_BOT),
    BOOKING_DELETE_BOOKING("/deletebook", BotIdentifier.BOOKING_BOT),
    BOOKING_NULL("null", BotIdentifier.BOOKING_BOT),
    HH_DECISION("/decision", BotIdentifier.HH_BOT),
    AVIA_START("/start", BotIdentifier.AVIA_BOT),
    AVIA_FLIGHT("/flight", BotIdentifier.AVIA_BOT),
    AVIA_BOARD("/board", BotIdentifier.AVIA_BOT),
    AVIA_SUBSCRIBE("subscribe", BotIdentifier.AVIA_BOT),
    AVIA_UNSUBSCRIBE("unsubscribe", BotIdentifier.AVIA_BOT),
    AVIA_ERROR("error", BotIdentifier.AVIA_BOT),
    ;

    private final String commandText;
    private final BotIdentifier botIdentifier;

}
