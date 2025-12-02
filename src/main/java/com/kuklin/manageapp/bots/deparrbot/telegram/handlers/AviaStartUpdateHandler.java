package com.kuklin.manageapp.bots.deparrbot.telegram.handlers;

import com.kuklin.manageapp.bots.deparrbot.telegram.AviaTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class AviaStartUpdateHandler implements AviaUpdateHandler {

    private static final String START_MESSAGE =
            """
                    /flight SU 123 - напиши в таком формате, чтобы получить информацию по рейсу.
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
