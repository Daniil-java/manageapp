package com.kuklin.manageapp.bots.pomidorotimer.models.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Priority {
    MUST,
    SHOULD,
    COULD,
    WOULD;
}
