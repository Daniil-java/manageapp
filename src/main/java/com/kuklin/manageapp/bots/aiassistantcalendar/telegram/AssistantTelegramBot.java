package com.kuklin.manageapp.bots.aiassistantcalendar.telegram;

import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class AssistantTelegramBot extends TelegramBot {
    @Autowired
    private AssistantTelegramFacade assistantTelegramFacade;

    public AssistantTelegramBot(TelegramAiAssistantCalendarBotKeyComponents telegramAiAssistantCalendarBotKeyComponents) {
        super(telegramAiAssistantCalendarBotKeyComponents.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        assistantTelegramFacade.handleUpdate(update);
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.ASSISTANT_BOT;
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.ASSISTANT_BOT.getBotUsername();
    }
}
