package com.kuklin.manageapp.bots.pomidorotimer.models.timer;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TimerStatus {
    PENDING,
    RUNNING,
    PAUSED,
    STOPPED,
    COMPLETE
}
