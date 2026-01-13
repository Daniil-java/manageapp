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
    PARSED,
    PROCESSED,
    NOTIFICATION_ERROR;
}
