package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.task;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.services.LocaleMessageService;
import com.kuklin.manageapp.bots.pomidorotimer.services.PomidoroUserService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TaskService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.MessageHandler;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters.MessageTypeConverter;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
public class TaskHandler implements MessageHandler {
    private final TaskService taskService;
    private final PomidoroTelegramBot telegramBot;
    private final PomidoroUserService userService;
    private final LocaleMessageService localeMessageService;
    private static final String BACK_MESSAGE = "reply.general.back";
    private static final String CLOSE_MESSAGE = "reply.general.close";
    private static final String ID_COMMAND = "command.task.id";
    private static final String NEXT_COMMAND = "command.task.list.next";
    private static final String PREV_COMMAND = "command.task.list.prev";

    /*
        Данная константа является стандартным значением
        строк в списке задач.
    */
    private final static int LIST_PAGE_ROW_COUNT = 8;

    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        int messageId = message.getMessageId();
        String userAnswer = message.getText();
        BotState botState = userEntity.getBotState();

        if (BotState.TASK.equals(botState)) {
            userEntity.setBotState(BotState.TASK_LIST);
            if (userEntity.getLastUpdatedTaskMessageId() != null) {
                telegramBot.sendMessage(new DeleteMessage(
                        String.valueOf(chatId),
                        userEntity.getLastUpdatedTaskMessageId().intValue()
                ));
            }
            telegramBot.sendMessage(new DeleteMessage(String.valueOf(chatId), messageId));
            List<Task> taskList = taskService.getParentDoneTasksByUserId(
                            userEntity.getId(),
                            PageRequest.of(0, LIST_PAGE_ROW_COUNT, Sort.by("id"))
            );
            Message futureMessage = telegramBot
                    .sendReturnedMessage(getList(taskList, chatId, 0, false));
            userEntity.setLastUpdatedTaskMessageId(Long.valueOf(futureMessage.getMessageId()));
            userService.updateUserEntity(userEntity);
            return null;
        }

        if (BotState.TASK_MAIN_MENU.equals(botState)) {
            EditMessageText editMessageText;
            List<Task> taskList;
            int page = 0;
            if (userAnswer.startsWith(localeMessageService.getMessage(NEXT_COMMAND))
                    || userAnswer.startsWith(localeMessageService.getMessage(PREV_COMMAND))) {
                page = userAnswer.startsWith(localeMessageService.getMessage(NEXT_COMMAND))
                        ? Integer.parseInt(userAnswer.substring(localeMessageService.getMessage(NEXT_COMMAND).length()))
                        : Integer.parseInt(userAnswer.substring(localeMessageService.getMessage(PREV_COMMAND).length()));
            }
            taskList = taskService.getParentDoneTasksByUserId(
                    userEntity.getId(),
                    PageRequest.of(page, LIST_PAGE_ROW_COUNT, Sort.by("id")));

            editMessageText = MessageTypeConverter
                    .convertSendToEdit(getList(taskList, chatId, page, false));
            editMessageText.setMessageId(userEntity.getLastUpdatedTaskMessageId().intValue());
            userEntity.setBotState(BotState.TASK_LIST);
            userService.updateUserEntity(userEntity);
            return editMessageText;
        }

        if (BotState.TASK_MAIN_MENU_CLOSE.equals(botState)) {
            if (userEntity.getLastUpdatedTaskMessageId() != null) {
                return new DeleteMessage(String.valueOf(chatId), userEntity.getLastUpdatedTaskMessageId().intValue());
            }
        }

        return BotApiMethodBuilder.makeSendMessage(chatId);
    }

    /*
        Метод getList(...) отвечает за предоставление информации
        о задачах, ввиде клавиатуры, в Телеграм.
    */
    public SendMessage getList
            (List<Task> taskList, long chatId, int page, boolean isSub) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);

        if (taskList.isEmpty()) {
            sendMessage.setText("You don't have any task");
            sendMessage.setReplyMarkup(getInlineMessageButtons());
        } else {
            sendMessage.setText("\uD83D\uDCCB Выберите задачу:");
            sendMessage.setReplyMarkup(
                    getInlineMessageListTaskButtons(taskList, LIST_PAGE_ROW_COUNT, page, isSub));
        }
        return sendMessage;
    }
    public InlineKeyboardMarkup getInlineMessageListTaskButtons(
            List<Task> taskList, int rowCount, int page, boolean isSub
    ) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        for (Task task: taskList) {
            InlineKeyboardButton taskButton = new InlineKeyboardButton(
                    String.format("[%s]: %s", task.getId(), task.getName())
            );
            taskButton.setCallbackData(localeMessageService.getMessage(ID_COMMAND) + task.getId());
            rowList.add(Arrays.asList(taskButton));
        }

        List<InlineKeyboardButton> row = new ArrayList<>();
        String suffix = isSub ? "sub" + taskList.get(0).getParent().getId() + "#" : "";
        if (page != 0) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton("⬅️");
            prevButton.setCallbackData(localeMessageService.getMessage(PREV_COMMAND) + suffix + (page - 1));
            row.add(prevButton);
        }
        if (taskList.size() == rowCount) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton("➡️");
            nextButton.setCallbackData(localeMessageService.getMessage(NEXT_COMMAND) + suffix + (page + 1));
            row.add(nextButton);
        }

        rowList.add(row);

        if (!isSub) {
            InlineKeyboardButton createButton = new InlineKeyboardButton("Создать");
            createButton.setCallbackData(BotState.TASK_CREATE.getCommand());
            rowList.add(Arrays.asList(createButton));
        } else {
            InlineKeyboardButton backButton = new InlineKeyboardButton(localeMessageService.getMessage(BACK_MESSAGE));
            backButton.setCallbackData(localeMessageService.getMessage(ID_COMMAND) + taskList.get(0).getParent().getId());
            rowList.add(Arrays.asList(backButton));
        }

        InlineKeyboardButton closeButton = new InlineKeyboardButton(localeMessageService.getMessage(CLOSE_MESSAGE));
        closeButton.setCallbackData(BotState.TASK_MAIN_MENU_CLOSE.getCommand());
        rowList.add(Arrays.asList(closeButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    private static InlineKeyboardMarkup getInlineMessageButtons() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton createButton = new InlineKeyboardButton("Создать");

        createButton.setCallbackData(BotState.TASK_CREATE.getCommand());

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(row1);

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(BotState.TASK, BotState.TASK_MAIN_MENU, BotState.TASK_MAIN_MENU_CLOSE);
    }
}
