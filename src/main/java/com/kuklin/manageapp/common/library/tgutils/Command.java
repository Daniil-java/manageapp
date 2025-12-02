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
    CALORIE_WEEK_LIST("\uD83D\uDCC5 НЕДЕЛЯ", BotIdentifier.CALORIE_BOT)
    ;

    private final String commandText;
    private final BotIdentifier botIdentifier;

}
