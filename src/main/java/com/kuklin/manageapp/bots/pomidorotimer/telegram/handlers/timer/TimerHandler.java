package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.timer;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Status;
import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerIntervalState;
import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerStatus;
import com.kuklin.manageapp.bots.pomidorotimer.services.TaskService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TimerService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.MessageHandler;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters.MessageTypeConverter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
public class TimerHandler implements MessageHandler {
    private final TimerService timerService;
    private final TaskService taskService;
    private final PomidoroTelegramBot telegramBot;
    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        int messageId = message.getMessageId();
        BotState botState = userEntity.getBotState();

        Timer timer = timerService.getOrCreateTimerByUserId(userEntity.getId()).get(0);
        List<Task> taskList = null;
        if (timer.getTasks() != null && !timer.getTasks().isEmpty()) {
            taskList = taskService.getTasksByTimerId(timer.getId());
        }

        String messageText = getTimerInfo(timer, taskList);
        EditMessageText editMessageText = BotApiMethodBuilder
                .makeEditMessageText(chatId, timer.getTelegramMessageId(), messageText, getInlineMessageTimerStatusButtons(timer.getStatus()));

        SendMessage replyMessage = MessageTypeConverter.convertEditToSend(editMessageText);

        if (BotState.TIMER.equals(botState)) {
            if (timer.getTelegramMessageId() != 0) {
                DeleteMessage deleteMessage =
                        new DeleteMessage(String.valueOf(chatId), timer.getTelegramMessageId());
                telegramBot.sendMessage(deleteMessage);
            }
            DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), messageId);
            telegramBot.sendMessage(deleteMessage);
            timerService.setMessageIdToAnyCompleteTimerByUserId(userEntity.getId(), telegramBot.sendReturnedMessage(replyMessage).getMessageId());
        }

        if (Arrays.asList(BotState.TIMER_START, BotState.TIMER_PAUSE, BotState.TIMER_STOP).contains(botState)) {
            TimerStatus timerStatus = TimerStatus.PAUSED;
            if (botState == BotState.TIMER_START) {
                timerStatus = TimerStatus.RUNNING;
            } else if (botState == BotState.TIMER_STOP) {
                timerStatus = TimerStatus.COMPLETE;
            }
            timer = timerService.updateTimerStatusOrNull(timer.getId(), timerStatus.name());
            editMessageText.setText(getTimerInfo(timer, taskList));
            editMessageText.setReplyMarkup(getInlineMessageTimerStatusButtons(timerStatus));
        }

        return editMessageText;
    }

    public static String getTimerInfo(Timer timer,  String firstText, List<Task> taskList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(firstText).append("\n");
        return stringBuilder.append(getTimerInfo(timer, taskList)).toString();
    }

    public static String getTimerInfo(Timer timer, List<Task> taskList) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\uD83D\uDCC5 <strong>Дата: </strong>");
        stringBuilder.append(timer.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE));
        stringBuilder.append("\n");

        stringBuilder.append("\uD83D\uDEA5 <strong>Интервал: </strong>").append((timer.getInterval() / 2) + 1);
        stringBuilder.append("\n");
        stringBuilder.append(TimerIntervalState.getTextTimerWorkTime(timer));
        stringBuilder.append("⌛ <strong>").append(TimerIntervalState.getTimerState(timer).getState()).append("</strong>");
        stringBuilder.append("\n");
        if (timer.getStatus().equals(TimerStatus.PAUSED)) {
            stringBuilder.append("\uD83D\uDCE2 <strong>Таймер остановлен</strong>");
            stringBuilder.append("\n");
        }

        if (!timer.getStatus().equals(TimerStatus.PENDING) && timer.getStopTime() != null) {
            String timeString = timer.getStopTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            stringBuilder.append("⏱ ").append("<strong>Время остановки: </strong>").append(timeString);
            stringBuilder.append("\n");
        }

        stringBuilder.append("\n");
        stringBuilder.append("\uD83D\uDCCB <strong>Задачи: </strong>");
        stringBuilder.append("\n");
        if (taskList != null && !taskList.isEmpty()) {
            for (Task task : taskList) {
                String emoji;
                if (task.getStatus().equals(Status.DONE)) emoji = "✅";
                else emoji = "❎";
                stringBuilder.append(String.format("%s [%s] %s", emoji, task.getPriority(), task.getName()));
                stringBuilder.append("\n");
                for (Task child : task.getChildTasks()) {
                    stringBuilder.append("      \uD83D\uDCAC ")
                            .append(String.format("[%s] %s", child.getPriority(), child.getName()
                    ));
                    stringBuilder.append("\n");
                }
            }
        }

        return stringBuilder.toString();
    }

    public static InlineKeyboardMarkup getInlineMessageTimerStatusButtons(TimerStatus timerStatus) {
        if (timerStatus.equals(TimerStatus.COMPLETE)) return null;
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        InlineKeyboardButton tasksListButton = new InlineKeyboardButton("\uD83D\uDCCB");
        InlineKeyboardButton settingsListButton = new InlineKeyboardButton("⚙");
        InlineKeyboardButton startButton = new InlineKeyboardButton("▶️");
        InlineKeyboardButton stopButton = new InlineKeyboardButton("⏹");
        InlineKeyboardButton pauseButton = new InlineKeyboardButton("⏸");

        startButton.setCallbackData(BotState.TIMER_START.getCommand());
        settingsListButton.setCallbackData(BotState.TIMER_SETTINGS.getCommand());
        tasksListButton.setCallbackData(BotState.TIMER_TASKS_LIST.getCommand());
        pauseButton.setCallbackData(BotState.TIMER_PAUSE.getCommand());
        stopButton.setCallbackData(BotState.TIMER_STOP.getCommand());

        rowList.add(Arrays.asList(settingsListButton, tasksListButton));
        switch (timerStatus) {
            case PENDING:
                rowList.add(Arrays.asList(startButton));
                break;
            case RUNNING:
                rowList.add(Arrays.asList(stopButton, pauseButton));
                break;
            case PAUSED:
                rowList.add(Arrays.asList(stopButton, startButton));
                break;
        }

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(
                BotState.TIMER, BotState.TIMER_START, BotState.TIMER_PAUSE, BotState.TIMER_STOP, BotState.TIMER_STATUS,
                BotState.TIMER_COMPLETE);

    }
}
