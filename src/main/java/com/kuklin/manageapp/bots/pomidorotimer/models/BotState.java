package com.kuklin.manageapp.bots.pomidorotimer.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BotState {
    START("/start"),
    TIMER("⏱️ TIMER"), TIMER_START("/timer_start"),
    TIMER_PAUSE("/timer_pause"), TIMER_STATUS("/timer_status"),
    TIMER_STOP("/timer_stop"),
    TIMER_COMPLETE("/timer_complete"),
    TIMER_TASKS_LIST("/timer_tasks_list"),
    TIMER_TASKS_LIST_DELETE("/timer_tasks_list_delete"),
    TIMER_TASKS_LIST_DONE("/timer_tasks_list_done"),
    TIMER_SETTINGS("/timer_settings"),
    TIMER_SETTINGS_WORK("/timer_settings_work"),
    TIMER_SETTINGS_SBREAK("/timer_settings_sbreak"),
    TIMER_SETTINGS_LBREAK("/timer_settings_lbreak"),
    TIMER_SETTINGS_LBREAK_INTERVAL("/timer_sttings_lbreak_interval"),
    TIMER_SETTINGS_AUTOSTART_WORK("/timer_settings_autostart_work"),
    TIMER_SETTINGS_AUTOSTART_BREAK("/timer_settings_autostart_break"),

    TIMER_PENDING("/timer_pending"),
    TASK("\uD83D\uDCCB MY TASKS"), TASK_CREATE("/task_create"),
    TASK_CREATE_SUBTASK("/task_create_subtask"),
    TASK_CREATE_PRIORITY("/task_create_priority"),
    TASK_CREATE_PRIORITY_SUBTASK("/task_create_priority_subtask"),

    TASK_CREATE_STATUS("/task_create_status"),
    TASK_CREATE_NAME("/task_create_name"),
    TASK_LIST("/task_list"),
    TASK_MAIN_MENU("/task_main_menu"), TASK_MAIN_MENU_CLOSE("/task_main_menu_close"),
    TASK_CREATE_COMMENT("/task_create_name"),
    PROCESSING(""),
    NOTIFICATION_TIMER_ALERT("/notification_timer_alert"), INFO("/info"), INFO_CLOSE("/info_close");

    private String command;

    public static BotState fromCommand(String command) {
        for (BotState state : BotState.values()) {
            if (state.getCommand().equals(command)) {
                return state;
            }
        }
        return BotState.PROCESSING;
    }
}
