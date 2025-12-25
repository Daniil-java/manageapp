package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.services.TaskService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TimerService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.timer.TimerHandler;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class TimerScheduleProcessor {
    private final TimerService timerService;
    private final TaskService taskService;
    private final PomidoroTelegramBot telegramBot;

    public void process() {
        log.info("Schedule timer checker is starting!");
        List<BotApiMethod> messages = checkTimersTimeStatus();
        if (!CollectionUtils.isEmpty(messages)) {
            log.info("Expired timer list size: {}", messages.size());
            for (BotApiMethod message: messages) {
                try {
                    Thread.sleep(1000);
                    telegramBot.sendMessage(message);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            log.info("Expired timer list is empty");
        }
    }

    @Transactional
    private List<BotApiMethod> checkTimersTimeStatus() {
        List<Timer> timers = timerService.getExpiredTimersAndUpdate();
        if (timers == null || timers.isEmpty()) return null;
        List<BotApiMethod> messages = new ArrayList<>();
        for (Timer timer: timers) {
            log.info(timer.getId() + " --- " + timer.getStatus());
            List<Task> taskList = null;
            if (!timer.getTasks().isEmpty()) {
                taskList = taskService.getTasksByTimerId(timer.getId());
            }
            messages.add(EditMessageText.builder()
                    .chatId(timer.getUserEntity().getChatId())
                    .messageId(timer.getTelegramMessageId())
                    .text(TimerHandler.getTimerInfo(timer, taskList))
                    .replyMarkup(TimerHandler.getInlineMessageTimerStatusButtons(timer.getStatus()))
                    .parseMode(ParseMode.HTML).build()
            );

            SendMessage sendMessage = new SendMessage(
                    String.valueOf(timer.getUserEntity().getChatId()),
                    "Timer has been expired!"
            );
            sendMessage.setReplyMarkup(getInlineMessageButtonDelete());
            messages.add(sendMessage);
        }
        return messages;
    }



    private InlineKeyboardMarkup getInlineMessageButtonDelete() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton deleteButton = new InlineKeyboardButton("OK");

        deleteButton.setCallbackData(BotState.NOTIFICATION_TIMER_ALERT.getCommand());

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(deleteButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(row1);


        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }
}
