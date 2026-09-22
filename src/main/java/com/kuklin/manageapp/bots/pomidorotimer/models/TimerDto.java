package com.kuklin.manageapp.bots.pomidorotimer.models;

import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Set;
@Data
@Accessors(chain = true)
public class TimerDto {
    private Long id;
    private UserDto user;
    private Set<TaskDto> tasks;
    @NotNull(message = "Status can't be empty!")
    private TimerStatus status;
    private int workDuration;
    private int shortBreakDuration;
    private int longBreakDuration;
    private int longBreakInterval;
    private boolean isAutostartWork;
    private boolean isAutostartBreak;
    private int interval;
    private LocalDateTime created;
}
