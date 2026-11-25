package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class AviaStartUpdateHandler implements AviaUpdateHandler {

    private static final String START_MESSAGE =
            """
                    /flight AK 880 (или /flight AK880) - чтобы получить информацию по рейсу.
                    /board Москва -> Хошимин, ГГГГ-ММ-ДД - чтобы получить расписание
                    """;
    private final AviaTelegramBot aviaTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        aviaTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), START_MESSAGE);
    }

    @Override
    public String getHandlerListName() {
        return Command.AVIA_START.getCommandText();
    }
}
