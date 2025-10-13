package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

//Обработчик непродусмотренных сообщений
@Component
@RequiredArgsConstructor
public class AviaErrorUpdateHandler implements AviaUpdateHandler {
    private static final String RESPONSE = "Данный запрос непредусмотрен сервисом.";
    private final AviaTelegramBot aviaTelegramBot;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        long chatId = update.hasCallbackQuery() ?
                update.getCallbackQuery().getMessage().getChatId() :
                update.getMessage().getChatId();

        aviaTelegramBot.sendReturnedMessage(
                chatId,
                RESPONSE
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.AVIA_ERROR.getCommandText();
    }
}
