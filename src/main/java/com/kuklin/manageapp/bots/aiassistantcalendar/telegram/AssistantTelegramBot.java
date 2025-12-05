package com.kuklin.manageapp.bots.aiassistantcalendar.telegram;

import com.kuklin.manageapp.bots.aiassistantcalendar.configurations.TelegramAiAssistantCalendarBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class AssistantTelegramBot extends TelegramBot {
    public static final BotIdentifier BOT_IDENTIFIER = BotIdentifier.ASSISTANT_BOT;
    @Autowired
    private AssistantTelegramFacade assistantTelegramFacade;
    @Autowired
    private AsyncService asyncService;

    public AssistantTelegramBot(TelegramAiAssistantCalendarBotKeyComponents telegramAiAssistantCalendarBotKeyComponents) {
        super(telegramAiAssistantCalendarBotKeyComponents.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        boolean result = doAsync(asyncService, update, u -> assistantTelegramFacade.handleUpdate(update));

        if (!result) {
            notifyAlreadyInProcess(update);
        }
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BOT_IDENTIFIER;
    }

    @Override
    public String getBotUsername() {
        return BOT_IDENTIFIER.getBotUsername();
    }
}
