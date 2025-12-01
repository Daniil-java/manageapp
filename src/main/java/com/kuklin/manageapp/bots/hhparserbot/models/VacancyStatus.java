package com.kuklin.manageapp.bots.hhparserbot.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VacancyStatus {
    APPLIED,
    REJECTED,
    PENDING,
    NOTIFICATED,

    CREATED,
    OPEN_AI_ERROR,
    NOT_FOUND_ERROR,
    PARSED,
    PROCESSED,
    NOTIFICATION_ERROR;
}
