package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.handler;

import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers.AssistantUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class AssistantHelpUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot telegramBot;
    private static final String HELP_MSG =
            """
                    <b>ИНСТРУКЦИЯ ПО РУЧНОЙ УСТАНОВКЕ КАЛЕНДАРЯ</b>
                                
                    1) Найди идентификатор календаря. Найти его можно в настройках Google Календаря → вкладка «Интеграция календаря».
                    
                    2) Укажи идентификатор календаря командой:
                    /set calendarId
                    
                    3) Дай боту доступ на изменение событий. В настройках календаря добавь этот адрес:
                    tgbot-calendar-assistante@tg-bot-assistent-calendar.iam.gserviceaccount.com
                    
                    <b>КОММАНДЫ:</b>
                    /auth - авторизация
                    /auth_status - узнать статус авторизации
                    /choosecalendar - выбор календаря
                    /today - получить список мероприятий на сегодня          
                    """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        telegramBot.sendReturnedMessage(update.getMessage().getChatId(), HELP_MSG);
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_HELP.getCommandText();
    }
}
