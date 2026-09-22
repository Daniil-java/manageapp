package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.task;

import com.kuklin.manageapp.bots.pomidorotimer.entities.Task;
import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Priority;
import com.kuklin.manageapp.bots.pomidorotimer.models.task.Status;
import com.kuklin.manageapp.bots.pomidorotimer.services.LocaleMessageService;
import com.kuklin.manageapp.bots.pomidorotimer.services.PomidoroUserService;
import com.kuklin.manageapp.bots.pomidorotimer.services.TaskService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.PomidoroTelegramBot;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers.MessageHandler;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
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
public class TaskCreationHandler implements MessageHandler {
    private final PomidoroUserService userService;
    private final TaskService taskService;
    private final PomidoroTelegramBot telegramBot;
    private final TaskHandler taskHandler;

    private LocaleMessageService localeMessageService;
    private static final String TASK_NAME_MESSAGE = "reply.task.create.name";
    private static final String TASK_COMMENT_MESSAGE = "reply.task.create.comment";
    private static final String TASK_PRIORITY_MESSAGE = "reply.task.create.priority";
    private static final String TASK_MUST_MESSAGE = "reply.task.must";
    private static final String TASK_SHOULD_MESSAGE = "reply.task.should";
    private static final String TASK_COULD_MESSAGE = "reply.task.could";
    private static final String TASK_WOULD_MESSAGE = "reply.task.would";
    private static final String TASK_SKIP_MESSAGE = "reply.task.skip";
    private static final String SKIP_COMMAND = "command.task.skip";

    @Override
    public BotApiMethod handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        int messageId = message.getMessageId();
        String userAnswer = message.getText();
        BotState botState = userEntity.getBotState();

        if (BotState.TASK_CREATE.equals(botState)) {
            EditMessageText editMessageText = BotApiMethodBuilder
                    .makeEditMessageText(chatId, messageId, localeMessageService.getMessage(TASK_PRIORITY_MESSAGE));
            editMessageText.setReplyMarkup(getInlineMessageButtonsPriority());

            userEntity.setBotState(BotState.TASK_CREATE_PRIORITY);
            userEntity.setLastUpdatedTaskId((long)messageId);
            userService.updateUserEntity(userEntity);
            return editMessageText;
        }

        if (BotState.TASK_CREATE_PRIORITY.equals(botState)) {
            Priority priority = Priority.valueOf(userAnswer);
            Task task = taskService.createTaskOrNull(
                    new Task()
                            .setUserEntity(userEntity)
                            .setPriority(priority)
                            .setStatus(Status.PLANNED)
            );
            userEntity.setLastUpdatedTaskId(task.getId());
            userEntity.setBotState(BotState.TASK_CREATE_NAME);
            userService.updateUserEntity(userEntity);

            return BotApiMethodBuilder
                    .makeEditMessageText(chatId, messageId, localeMessageService.getMessage(TASK_NAME_MESSAGE));
        }

        if (BotState.TASK_CREATE_NAME.equals(botState)) {
            Task task = taskService.getTaskByIdOrNull(userEntity.getLastUpdatedTaskId());
            String name = userAnswer;
            task.setName(name);
            taskService.createTaskOrNull(task);
            userEntity.setBotState(BotState.TASK_CREATE_COMMENT);
            userService.updateUserEntity(userEntity);

            DeleteMessage deleteMessage = new DeleteMessage(chatId.toString(), messageId);
            telegramBot.sendMessage(deleteMessage);

            EditMessageText editMessageText = BotApiMethodBuilder
                    .makeEditMessageText(chatId, userEntity.getLastUpdatedTaskMessageId().intValue(), localeMessageService.getMessage(TASK_COMMENT_MESSAGE));
            editMessageText.setReplyMarkup(getInlineMessageSkipButton());
            return editMessageText;
        }

        if (BotState.TASK_CREATE_COMMENT.equals(botState)) {
            if (!userAnswer.startsWith(localeMessageService.getMessage(SKIP_COMMAND))) {
                DeleteMessage deleteMessage = new DeleteMessage(chatId.toString(), messageId);
                telegramBot.sendMessage(deleteMessage);

                Task task = taskService.getTaskByIdOrNull(userEntity.getLastUpdatedTaskId());
                String comment = userAnswer;
                task.setComment(comment);
                taskService.createTaskOrNull(task);
            }

            userEntity.setBotState(BotState.TASK_LIST)
                    .setLastUpdatedTaskId(null);
            userService.updateUserEntity(userEntity);
            userEntity.setBotState(BotState.TASK_MAIN_MENU);
            return taskHandler.handle(message, userEntity);
        }

        return BotApiMethodBuilder.makeSendMessage(chatId);
    }

    public InlineKeyboardMarkup getInlineMessageSkipButton() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        InlineKeyboardButton skipButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_SKIP_MESSAGE));
        skipButton.setCallbackData(localeMessageService.getMessage(SKIP_COMMAND));
        rowList.add(Arrays.asList(skipButton));
        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    public InlineKeyboardMarkup getInlineMessageButtonsPriority() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton mustButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_MUST_MESSAGE));
        InlineKeyboardButton shouldButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_SHOULD_MESSAGE));
        InlineKeyboardButton couldButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_COULD_MESSAGE));
        InlineKeyboardButton wouldButton = new InlineKeyboardButton(localeMessageService.getMessage(TASK_WOULD_MESSAGE));

        mustButton.setCallbackData(localeMessageService.getMessage(TASK_MUST_MESSAGE));
        shouldButton.setCallbackData(localeMessageService.getMessage(TASK_SHOULD_MESSAGE));
        couldButton.setCallbackData(localeMessageService.getMessage(TASK_COULD_MESSAGE));
        wouldButton.setCallbackData(localeMessageService.getMessage(TASK_WOULD_MESSAGE));

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(mustButton);
        row1.add(shouldButton);
        row1.add(couldButton);
        row1.add(wouldButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(row1);

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

    public InlineKeyboardMarkup getInlineMessageButtonsPriority(long taskId) {
        InlineKeyboardMarkup keyboardMarkup = getInlineMessageButtonsPriority();
        for (InlineKeyboardButton button: keyboardMarkup.getKeyboard().get(0)) {
            button.setCallbackData(button.getCallbackData() + "_" + taskId);
        }
        return keyboardMarkup;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(BotState.TASK_CREATE, BotState.TASK_CREATE_NAME, BotState.TASK_CREATE_PRIORITY, BotState.TASK_CREATE_COMMENT);
    }
}
