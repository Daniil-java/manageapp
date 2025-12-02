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
    ASSISTANT_DELETE("/delete", BotIdentifier.ASSISTANT_BOT)
    ;

    private final String commandText;
    private final BotIdentifier botIdentifier;

}
