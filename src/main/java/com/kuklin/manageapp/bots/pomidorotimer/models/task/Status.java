package com.kuklin.manageapp.bots.pomidorotimer.models.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Status {
    PLANNED,
    IN_PROGRESS,
    DONE;
}
