package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.task;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Priority;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Status;
import com.kuklin.manageapp.bots.pomidorotimer.services.LocaleMessageService;
import com.kuklin.manageapp.bots.pomidorotimer.services.PomidoroUserService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TaskService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TimerService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.MessageHandler;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters.MessageTypeConverter;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters.NumeralConverter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class TaskListHandler implements MessageHandler {
    private final PomidoroUserService userService;
    private final PomidoroTelegramBot telegramBot;
    private final TaskService taskService;
    private final TimerService timerService;
    private final TaskHandler taskHandler;
    private final TaskCreationHandler taskCreationHandler;
    private final LocaleMessageService localeMessageService;
    private static final String TASK_NAME_MESSAGE = "reply.task.create.name";
    private static final String TASK_PRIORITY_MESSAGE = "reply.task.create.priority";
    private static final String TASK_BIND_MESSAGE = "reply.task.bind";
    private static final String TASK_ACCEPT_MESSAGE = "reply.task.accept";
    private static final String TASK_REJECT_MESSAGE = "reply.task.reject";
    private static final String BACK_MESSAGE = "reply.general.back";
    private static final String DELETE_MESSAGE = "reply.general.delete";
    private static final String DONE_MESSAGE = "reply.task.done";
    private static final String CREATE_SUB_MESSAGE = "reply.task.create.sub";
    private static final String GENERATE_MESSAGE = "reply.task.generation";
    private static final String SUBTASK_MESSAGE = "reply.task.subtasks";
    private static final String DELETE_COMMAND = "command.task.delete";
    private static final String DONE_COMMAND = "command.task.done";
    private static final String ID_COMMAND = "command.task.id";
    private static final String BIND_COMMAND = "command.task.bind";
    private static final String SUBTASK_COMMAND = "command.task.subtask";
    private static final String GENERATE_COMMAND = "command.task.generate";
    private static final String ACCEPT_COMMAND = "command.task.accept";
    private static final String REJECT_COMMAND = "command.task.reject";
    private static final String GET_SUBS_COMMAND = "command.task.getsubs";
    private static final String NEXT_COMMAND = "command.task.list.next";
    private static final String NEXT_SUB_COMMAND = "command.task.list.nextsub";
    private static final String PREV_COMMAND = "command.task.list.prev";
    private static final String PREV_SUB_COMMAND = "command.task.list.prevsub";

    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        int messageId = message.getMessageId();
        String userAnswer = message.getText();
        BotState botState = userEntity.getBotState();

        EditMessageText editMessageText = BotApiMethodBuilder.makeEditMessageText(chatId, userEntity.getLastUpdatedTaskMessageId().intValue());

        if (BotState.TASK_LIST.equals(botState)) {
            if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(ID_COMMAND))) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(ID_COMMAND).length()));
                Task task = taskService.getTaskByIdOrNull(taskId);
                editMessageText.setText(getTaskInfo(task));
                editMessageText.setReplyMarkup(getInlineMessageButtons(taskId));
                return editMessageText;
            }

            if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(DELETE_COMMAND))) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(DELETE_COMMAND).length()));
                taskService.deleteTaskById(taskId);
                userEntity.setBotState(BotState.TASK_MAIN_MENU);
                return taskHandler.handle(message, userEntity);
            }
            if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(BIND_COMMAND))) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(BIND_COMMAND).length()));
                long timerId = timerService.getAnyNotCompleteTimerByUserIdOrNull(userEntity.getId()).get(0).getId();
                timerService.bindTaskToTimerOrNull(timerId, taskId);
                return new SendMessage(String.valueOf(chatId), localeMessageService.getMessage(TASK_BIND_MESSAGE));
            }
            if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(DONE_COMMAND))) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(DONE_COMMAND).length()));
                Task task = taskService.getTaskByIdOrNull(taskId);
                task.setStatus(Status.DONE);
                taskService.createTaskOrNull(task);
                editMessageText.setText(getTaskInfo(task));
                editMessageText.setReplyMarkup(getInlineMessageButtons(taskId));
                return editMessageText;
            }

            if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(SUBTASK_COMMAND))) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(SUBTASK_COMMAND).length()));
                editMessageText.setText(localeMessageService.getMessage(TASK_PRIORITY_MESSAGE));
                editMessageText.setReplyMarkup(taskCreationHandler.getInlineMessageButtonsPriority(taskId));
                return editMessageText;
            }
            if (!userAnswer.isEmpty() && checkPriority(userAnswer)) {
                long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(userAnswer.indexOf("_") + 1));
                String priority = userAnswer.substring(0, userAnswer.indexOf("_"));
                Task task = taskService.createTaskOrNull(
                        new Task()
                                .setUserEntity(userEntity)
                                .setPriority(Priority.valueOf(priority))
                                .setStatus(Status.PLANNED)
                                .setParent(new Task().setId(taskId))
                );
                userEntity.setLastUpdatedTaskId(task.getId());
                userEntity.setBotState(BotState.TASK_CREATE_SUBTASK);
                userService.updateUserEntity(userEntity);

                return BotApiMethodBuilder
                        .makeEditMessageText(
                                chatId,
                                userEntity.getLastUpdatedTaskMessageId().intValue(),
                                localeMessageService.getMessage(TASK_NAME_MESSAGE)
                        );
            }

        }
        if (BotState.TASK_CREATE_SUBTASK.equals(botState)) {
            Task task = taskService.getTaskByIdOrNull(userEntity.getLastUpdatedTaskId());
            String name = userAnswer, comment = null;
            if (!userAnswer.isEmpty() && userAnswer.indexOf("#") != -1) {
                name = userAnswer.substring(0, userAnswer.indexOf("#"));
                comment = userAnswer.substring(userAnswer.indexOf("#") + 1, userAnswer.length() - 1);
            }
            task.setName(name).setComment(comment);
            taskService.createTaskOrNull(task);
            userEntity.setBotState(BotState.TASK_LIST);
            userEntity.setLastUpdatedTaskId(null);
            userService.updateUserEntity(userEntity);

            DeleteMessage deleteMessage = new DeleteMessage(chatId.toString(), messageId);
            telegramBot.sendMessage(deleteMessage);
            userEntity.setBotState(BotState.TASK_MAIN_MENU);
            return taskHandler.handle(message, userEntity);
        }

        if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(GENERATE_COMMAND))) {
            long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(GENERATE_COMMAND).length()));
            List<Task> taskList = taskService.generateSubtasksByIdOrNull(taskId);
            editMessageText.setText(getTaskListInfo(taskList));
            editMessageText.setReplyMarkup(getInlineMessageAcceptButtons(taskId, taskList));
            return editMessageText;
        }

        if (!userAnswer.isEmpty() &&
                (userAnswer.startsWith(localeMessageService.getMessage(ACCEPT_COMMAND)) || userAnswer.startsWith(localeMessageService.getMessage(REJECT_COMMAND)))) {
            long taskId;
            if (userAnswer.startsWith(localeMessageService.getMessage(ACCEPT_COMMAND))) {
                taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(ACCEPT_COMMAND).length()));
                message.setText(localeMessageService.getMessage(ID_COMMAND) + taskId);
                handle(message, userEntity);
            } else {
                taskId = NumeralConverter.parsePositiveSafelyLong(
                        userAnswer.substring(localeMessageService.getMessage(REJECT_COMMAND).length(), userAnswer.indexOf("#"))
                );
                String[] subIds = userAnswer
                        .substring(userAnswer.indexOf("#") + 1).split("#");
                List<Long> substackIds = new ArrayList<>();
                for (int i = 0; i < subIds.length; i++) {
                    substackIds.add(NumeralConverter.parsePositiveSafelyLong(subIds[i]));
                }
                taskService.deleteAllById(substackIds);
            }
            message.setText(localeMessageService.getMessage(ID_COMMAND) + taskId);
            return handle(message, userEntity);
        }
        if (!userAnswer.isEmpty() && userAnswer.startsWith(localeMessageService.getMessage(GET_SUBS_COMMAND))) {
            long taskId = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(localeMessageService.getMessage(GET_SUBS_COMMAND).length()));
            List<Task> taskList = taskService.getChildTasksByTaskId(taskId,
                    PageRequest.of(0, 8, Sort.by("id")));
            editMessageText = MessageTypeConverter
                    .convertSendToEdit(taskHandler.getList(taskList, chatId, 0, true));
            editMessageText.setMessageId(userEntity.getLastUpdatedTaskMessageId().intValue());
            return editMessageText;
        }
        if (!userAnswer.isEmpty() && (userAnswer.startsWith(localeMessageService.getMessage(NEXT_SUB_COMMAND))
                || userAnswer.startsWith(localeMessageService.getMessage(PREV_SUB_COMMAND)))) {
            String command = userAnswer.startsWith(localeMessageService.getMessage(NEXT_SUB_COMMAND))
                    ? localeMessageService.getMessage(NEXT_SUB_COMMAND)
                    : localeMessageService.getMessage(PREV_SUB_COMMAND);
            long id = NumeralConverter.parsePositiveSafelyLong(userAnswer.substring(command.length(), userAnswer.indexOf("#")));
            int page = NumeralConverter.parsePositiveSafelyInt(userAnswer.substring(userAnswer.indexOf("#") + 1));
            List<Task> taskList = taskService.getChildTasksByTaskId(
                    id,
                    PageRequest.of(page, 8, Sort.by("id")));

            editMessageText = MessageTypeConverter
                    .convertSendToEdit(taskHandler.getList(taskList, chatId, page, true));
            editMessageText.setMessageId(userEntity.getLastUpdatedTaskMessageId().intValue());
            return editMessageText;
        }
        if (!userAnswer.isEmpty() && (userAnswer.startsWith(localeMessageService.getMessage(NEXT_COMMAND))
                || userAnswer.startsWith(localeMessageService.getMessage(PREV_COMMAND)) ||
                userAnswer.startsWith("/subnext") || userAnswer.startsWith("/subnext"))) {
            userEntity.setBotState(BotState.TASK_MAIN_MENU);
            return taskHandler.handle(message, userEntity);
        }
        return BotApiMethodBuilder.makeSendMessage(chatId);
    }

    private String getTaskInfo(Task task) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("<strong>[%s] %s</strong>", task.getPriority(), task.getName()));
        stringBuilder.append("\n");
        stringBuilder.append("\uD83D\uDCC5 <strong>Created on: </strong>").append(task.getCreated().format(DateTimeFormatter.ISO_DATE));
        stringBuilder.append("\n");
        if (task.getComment() != null) {
            stringBuilder.append(task.getComment());
            stringBuilder.append("\n");
        }

        if (!task.getChildTasks().isEmpty()) {
            stringBuilder.append("\uD83D\uDCCB <strong>Subtasks: </strong>");
            stringBuilder.append("\n");
            for (Task t: task.getChildTasks()) {
                stringBuilder.append(String.format("        [%s] %s", t.getPriority(), t.getName()));
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    private String getTaskListInfo(List<Task> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Task task: list) {
            stringBuilder
                    .append(task.getName() + "\n")
                    .append(task.getStatus() + "\n")
                    .append(task.getComment() + "\n\n");
        }
        return stringBuilder.toString();
    }
    private boolean checkPriority(String str) {
        return str.startsWith(Priority.MUST.name()) || str.startsWith(Priority.COULD.name()) ||
                str.startsWith(Priority.WOULD.name()) || str.startsWith(Priority.SHOULD.name());
    }

    public InlineKeyboardMarkup getInlineMessageAcceptButtons(long taskId, List<Task> list) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        InlineKeyboardButton acceptButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_ACCEPT_MESSAGE));
        InlineKeyboardButton rejectButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_REJECT_MESSAGE));
        StringBuilder stringBuilder = new StringBuilder();
        for (Task task: list) {
            stringBuilder.append("#" + task.getId());
        }

        acceptButton.setCallbackData(localeMessageService.getMessage(ACCEPT_COMMAND) + taskId);
        rejectButton.setCallbackData(localeMessageService.getMessage(REJECT_COMMAND) + taskId + stringBuilder.toString());

        rowList.add(Arrays.asList(acceptButton, rejectButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup getInlineMessageButtons(long taskId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backButton = new InlineKeyboardButton(localeMessageService.getMessage(BACK_MESSAGE));
        InlineKeyboardButton deleteButton = new InlineKeyboardButton(localeMessageService.getMessage(DELETE_MESSAGE));
        InlineKeyboardButton addToTimerButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_BIND_MESSAGE));
        InlineKeyboardButton doneButton = new InlineKeyboardButton(localeMessageService.getMessage(DONE_MESSAGE));
        InlineKeyboardButton subtaskButton = new InlineKeyboardButton(localeMessageService.getMessage(CREATE_SUB_MESSAGE));
        InlineKeyboardButton subtaskGenerationButton = new InlineKeyboardButton(localeMessageService.getMessage(GENERATE_MESSAGE));
        InlineKeyboardButton getSubtasksButton = new InlineKeyboardButton(localeMessageService.getMessage(SUBTASK_MESSAGE));

        backButton.setCallbackData(BotState.TASK_MAIN_MENU.getCommand());
        deleteButton.setCallbackData(localeMessageService.getMessage(DELETE_COMMAND) + taskId);
        addToTimerButton.setCallbackData(localeMessageService.getMessage(BIND_COMMAND) + taskId);
        doneButton.setCallbackData(localeMessageService.getMessage(DONE_COMMAND) + taskId);
        subtaskButton.setCallbackData(localeMessageService.getMessage(SUBTASK_COMMAND) + taskId);
        subtaskGenerationButton.setCallbackData(localeMessageService.getMessage(GENERATE_COMMAND) + taskId);
        getSubtasksButton.setCallbackData(localeMessageService.getMessage(GET_SUBS_COMMAND) + taskId);

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(doneButton);
        row1.add(deleteButton);
        row1.add(addToTimerButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(row1);
        rowList.add(Arrays.asList(getSubtasksButton, subtaskButton, subtaskGenerationButton));
        rowList.add(Arrays.asList(backButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(BotState.TASK_LIST, BotState.TASK_CREATE_SUBTASK);
    }
}
