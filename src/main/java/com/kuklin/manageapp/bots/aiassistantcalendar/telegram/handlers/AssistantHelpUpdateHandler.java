package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class AssistantHelpUpdateHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot telegramBot;
    private final UserMessagesLogService userMessagesLogService;
    private static final String HELP_MSG =
            """
                    <b>ИНСТРУКЦИЯ ПО РУЧНОЙ УСТАНОВКЕ КАЛЕНДАРЯ</b>
                               
                    1) Дай боту доступ на изменение событий. В настройках календаря, в разделе "Имеют доступ", добавь этот адрес:
                    tgbot-calendar-assistante@tg-bot-assistent-calendar.iam.gserviceaccount.com
                                
                    2) Найди идентификатор календаря. Найти его можно в настройках Google Календаря → вкладка «Интеграция календаря».
                    
                    3) Укажи идентификатор календаря командой:
                    /set calendarId
                    
                    <b>КОММАНДЫ:</b>
                    /auth - авторизация
                    /auth_status - узнать статус авторизации
                    /choosecalendar - выбор календаря
                    /settings - настройки часового пояса и ежедневных напоминаний         
                    """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        telegramBot.sendReturnedMessage(update.getMessage().getChatId(), HELP_MSG);
        userMessagesLogService.createLog(
                telegramUser.getTelegramId(),
                telegramUser.getUsername(),
                telegramUser.getFirstname(),
                telegramUser.getLastname(),
                update.getMessage().getText()
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_HELP.getCommandText();
    }
}
