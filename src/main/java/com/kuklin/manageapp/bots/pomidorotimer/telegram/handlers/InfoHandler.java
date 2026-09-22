package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.Timer;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Status;
import com.kuklin.manageapp.bots.pomidorotimer.models.timer.TimerIntervalState;
import com.kuklin.manageapp.bots.pomidorotimer.services.TimerService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class InfoHandler implements MessageHandler {
    private final TimerService timerService;
    private final PomidoroTelegramBot telegramBot;

    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        String chatId = String.valueOf(message.getChatId());
        int messageId = message.getMessageId();
        String userAnswer = message.getText();
        BotState botState = userEntity.getBotState();

        telegramBot.sendMessage(new DeleteMessage(chatId, messageId));

        if (BotState.INFO.equals(botState)) {
            if (userAnswer != null && userAnswer.startsWith("/info_close")) {
                int msgId = Integer.parseInt(userAnswer.substring("/info_close".length()));
                return new DeleteMessage(chatId, msgId);
            }
            LocalDateTime localDateTime = LocalDateTime.now().minusDays(90);
            List<Timer> timerList = timerService.
                    getCompletedTimersByUserIdAndCreatedAfterDate(userEntity.getId(), localDateTime);

            for (Timer timer: timerList) {
                if (timer.getInterval() == 0) continue;
                String answer = getTimerInfo(timer);
                SendMessage sendMessage = new SendMessage(chatId, answer);
                sendMessage.setReplyMarkup(getInlineMessageButtons(timer.getId()));
                sendMessage.enableMarkdown(true);
                sendMessage.setParseMode(ParseMode.HTML);
                telegramBot.sendMessage(sendMessage);

                ThreadUtil.sleep(200);

            }

        }
        return null;
    }

    public static List<String> getTimerTasksInfo(List<Timer> timerList) {
        List<String> infoList = new ArrayList<>();
        for (Timer t: timerList) {
            if (t.getInterval() == 0) continue;
            StringBuilder builder = new StringBuilder();

            builder.append("\uD83D\uDCC5 <strong>Дата: </strong>");
            builder.append(t.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE));

            builder.append("\n");
            builder.append(TimerIntervalState.getTextTimerInfo(t));
            builder.append("\n");
            builder.append("\uD83D\uDCCB <strong>Задачи: </strong>").append("\n");
            if (!t.getTasks().isEmpty()) {
                for (Task task: t.getTasks()) {
                    String emoji;
                    if (task.getStatus().equals(Status.DONE)) emoji = "✅";
                    else emoji = "❎";
                    builder.append(String.format("      %s [%s] %s", emoji, task.getPriority(), task.getName()));
                    builder.append("\n");
                }
            }

            infoList.add(builder.toString());
        }
        return infoList;
    }

    public static String getTimerInfo(Timer timer) {
        if (timer.getInterval() == 0) return "";
        StringBuilder builder = new StringBuilder();
        builder.append("\uD83D\uDCC5 <strong>Дата: </strong>");
        builder.append(timer.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE));
        builder.append("\n");
        builder.append(TimerIntervalState.getTextTimerInfo(timer));
        builder.append("\n");
        builder.append("\uD83D\uDCCB <strong>Задачи: </strong>");
        builder.append("\n");
        if (!timer.getTasks().isEmpty()) {
            for (Task task: timer.getTasks()) {
                String emoji;
                if (task.getStatus().equals(Status.DONE)) emoji = "✅";
                else emoji = "❎";
                builder.append(String.format("      %s [%s] %s", emoji, task.getPriority(), task.getName()));
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    public static InlineKeyboardMarkup getInlineMessageButtons(long timerId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        InlineKeyboardButton closeButton = new InlineKeyboardButton("Ок");
        closeButton.setCallbackData(BotState.INFO_CLOSE.getCommand() + timerId);

        rowList.add(Arrays.asList(closeButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }


    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(BotState.INFO);
    }
}
