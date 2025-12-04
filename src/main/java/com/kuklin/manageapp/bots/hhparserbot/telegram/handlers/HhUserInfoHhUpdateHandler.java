package com.kuklin.manageapp.bots.hhparserbot.telegram.handlers;

import com.kuklin.manageapp.bots.hhparserbot.services.HhUserInfoService;
import com.kuklin.manageapp.bots.hhparserbot.telegram.HhTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HhUserInfoHhUpdateHandler implements HhUpdateHandler {
    @Autowired
    @Lazy
    private HhTelegramBot telegramBot;
    @Autowired
    private HhUserInfoService hhUserInfoService;

    private final static String USER_INFO_COMMAND = "/about";
    private final static String RESPONSE = "Информация сохранена";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String messageText = message.getText();

        hhUserInfoService.setInfo(telegramUser.getTelegramId(), messageText.substring(USER_INFO_COMMAND.length()));
        telegramBot.sendReturnedMessage(chatId, RESPONSE);
    }

    @Override
    public String getHandlerListName() {
        return USER_INFO_COMMAND;
    }
}
