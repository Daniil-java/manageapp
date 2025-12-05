package com.kuklin.manageapp.bots.pomidorotimer.telegram.handlers;

import com.kuklin.manageapp.bots.pomidorotimer.entities.UserEntity;
import com.kuklin.manageapp.bots.pomidorotimer.models.BotState;
import com.kuklin.manageapp.bots.pomidorotimer.services.LocaleMessageService;
import com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.BotApiMethodBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class StartHandler implements MessageHandler {
    @Autowired
    private LocaleMessageService localeMessageService;
    private static final String START_MESSAGE =
            "⏱️ Focus better. 📋 Plan smarter.\n\nI’m your Pomodoro timer and task tracker:\n• Configurable work/break sessions\n• Priorities (MUST/SHOULD/COULD/WOULD)\n• Subtasks\n• AI can auto-split tasks\n\nUse the menu below to start.";

    @Override
    public SendMessage handle(Message message, UserEntity userEntity) {
        Long chatId = message.getChatId();
        BotState botState = userEntity.getBotState();
        SendMessage replyMessage = BotApiMethodBuilder.makeSendMessage(chatId);
        replyMessage.setReplyMarkup(mainMenuKeyboard());

        if (BotState.START.equals(botState)) {
            replyMessage.setText(START_MESSAGE);
        }

        return replyMessage;
    }

    @Override
    public List<BotState> getHandlerListName() {
        return Arrays.asList(BotState.START);
    }

    private ReplyKeyboardMarkup mainMenuKeyboard() {
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setResizeKeyboard(true);
        kb.setOneTimeKeyboard(false);
        kb.setSelective(false);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BotState.TIMER.getCommand()));
        row1.add(new KeyboardButton(BotState.TASK.getCommand()));

        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(row1);
        kb.setKeyboard(rows);
        return kb;
    }
}
