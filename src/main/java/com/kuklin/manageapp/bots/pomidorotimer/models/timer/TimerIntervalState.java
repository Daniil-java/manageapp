package com.kuklin.manageapp.bots.pomidorotimer.models.timer;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TimerIntervalState {
    WORK_INTERVAL("Рабочее время"),
    SHORT_BREAK_INTERVAL("Перерыв"),
    LONG_BREAK_INTERVAL("Большой перерыв"),
    NOT_LAUNCH("Таймер не запущен");

    private String state;

    public static TimerIntervalState getTimerState(Timer timer) {
        if (timer.getStatus().equals(TimerStatus.PENDING)) {
            return NOT_LAUNCH;
        }
        if (timer.getLongBreakInterval() != 0
                && timer.getInterval() % 2 != 0
                && timer.getInterval() / 2 % timer.getLongBreakInterval() != 0) {
            return LONG_BREAK_INTERVAL;
        } else if (timer.getLongBreakInterval() != 0 && timer.getInterval() % 2 != 0 ) {
            return SHORT_BREAK_INTERVAL;
        } else {
            return WORK_INTERVAL;
        }
    }

    public static String getTextTimerInfo(Timer timer) {
        StringBuilder builder = new StringBuilder();
        if (timer.getInterval() == 0) return "";

        int interval = timer.getInterval() + 1;
        int workInt = interval / 2 + interval % 2;
        int breakInt = interval - workInt;
        int longBreakInt = 0;
        if (timer.getLongBreakInterval() != 0) {
            longBreakInt = breakInt - breakInt / timer.getLongBreakInterval();
        }
        int shortBreakInt = breakInt - longBreakInt;

        builder.append("\uD83D\uDCBC <strong>Рабочих интервалов: </strong>").append(workInt).append("\n");
        builder.append("✋ <strong>Коротких перерывов: </strong>").append(shortBreakInt).append("\n");
        builder.append("\uD83D\uDEA7 <strong>Длинных перерывов: </strong>").append(longBreakInt).append("\n");
        builder.append("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDCBB <strong>Общее рабочее время:</strong> ").append(textFormatTime(workInt * timer.getWorkDuration())).append("\n");
        builder.append("☕ <strong>Общее время перерывов:</strong> ")
                .append(textFormatTime(longBreakInt * timer.getLongBreakDuration() + shortBreakInt * timer.getShortBreakDuration()))
                .append("\n");

        return builder.toString();
    }

    public static String getTextTimerWorkTime(Timer timer) {
        StringBuilder builder = new StringBuilder();
        int interval = timer.getInterval() + 1;
        int workInt = interval / 2 + interval % 2;
        builder.append("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDCBB <strong>Общее рабочее время:</strong> ")
                .append(textFormatTime(workInt * timer.getWorkDuration())).append("\n");

        return builder.toString();
    }

    private static String textFormatTime(int minutes) {
        int hours = minutes / 60;
        int min = minutes - hours * 60;
        return String.format("%s ч. %s мин.", hours, min);
    }
}
