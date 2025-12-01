package com.kuklin.manageapp.common.library.tgutils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum BotIdentifier {
    CALORIE_BOT("@track_your_work_bot"),
    ASSISTANT_BOT("@personal_calen_bot"),
    KWORK("@kworker_parserbot"),
    BOOKING_BOT("@slotmanage_bot"),
    HH_BOT("@workhunttg_bot"),
    POMIDORO_BOT("@taskGPT_Bot"),
    AVIA_BOT("@deparr_bot"),
    PAYMENT("@payment")
    ;

    private final String botUsername;
}
