package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum BotIdentifier {
    CALORIE_BOT("@track_your_work_bot");

    private final String botUsername;
}
