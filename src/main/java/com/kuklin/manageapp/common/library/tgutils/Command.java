package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum Command {
    CALORIE_START("/start", BotIdentifier.CALORIE_BOT),
    CALORIE_DELETE("/deleteDish", BotIdentifier.CALORIE_BOT),
    CALORIE_GENERAL("/general", BotIdentifier.CALORIE_BOT),
    CALORIE_TODAY_LIST("/today", BotIdentifier.CALORIE_BOT)
    ;

    private final String commandText;
    private final BotIdentifier botIdentifier;
}
