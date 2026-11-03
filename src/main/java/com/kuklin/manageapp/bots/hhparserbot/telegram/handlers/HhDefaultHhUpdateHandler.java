package com.kuklin.manageapp.bots.hhparserbot.telegram.handlers;

import com.kuklin.manageapp.bots.hhparserbot.entities.HhUserInfo;
import com.kuklin.manageapp.bots.hhparserbot.services.HhUserInfoService;
import com.kuklin.manageapp.bots.hhparserbot.services.HhWorkFilterService;
import com.kuklin.manageapp.bots.hhparserbot.telegram.HhTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HhDefaultHhUpdateHandler implements HhUpdateHandler {
    @Autowired
    private HhWorkFilterService workFilterService;
    @Autowired
    @Lazy
    private HhTelegramBot telegramBot;

    @Autowired
    private HhUserInfoService hhUserInfoService;
    private final static String RESPONSE_SUCCESS = "Ссылка сохранена";
    private final static String RESPONSE_FAIL = "Произошла ошибка";
    public final static String HANDLER_NAME = "/defaultHandler";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String messageText = message.getText();

        String answer;
        if (isValidatedUrl(messageText)) {
            HhUserInfo hhUserInfo = hhUserInfoService
                    .getHhUserInfoByTelegramIdOrCreate(telegramUser.getTelegramId());
            workFilterService.create(hhUserInfo, messageText);
            answer = RESPONSE_SUCCESS;
        } else {
            answer = RESPONSE_FAIL;
        }

        telegramBot.sendReturnedMessage(chatId, answer);
    }

    private boolean isValidatedUrl(String text) {
        return text.startsWith("hh.ru/vacancies/")
                || text.startsWith("https://hh.ru/vacancies/")
                || text.startsWith("https://hh.ru/search/vacancy");
    }

    @Override
    public String getHandlerListName() {
        return HANDLER_NAME;
    }
}
