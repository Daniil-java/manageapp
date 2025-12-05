package com.kuklin.manageapp.bots.aiassistantcalendar.telegram.handlers;

import com.kuklin.manageapp.bots.aiassistantcalendar.services.UserMessagesLogExportService;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AssistantUserLogHandler implements AssistantUpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final UserMessagesLogExportService exportService;
    private static final Set<Long> ADMIN_IDS = Set.of(
            425120436L //kuklin_daniil
    );
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!ADMIN_IDS.contains(telegramUser.getTelegramId())) {
            return;
        }
        Long chatId = update.getMessage().getChatId();
        assistantTelegramBot.sendChatActionTyping(chatId);

        try {
            byte[] fileBytes = exportService.exportAllAsXlsx();
            if (fileBytes == null || fileBytes.length == 0) {
                assistantTelegramBot.sendReturnedMessage(chatId, "Лог пустой 🤷‍♂️");
                return;
            }

            assistantTelegramBot.sendDocument(
                    chatId,
                    fileBytes,
                    "user_messages_log.xlsx",
                    "Лог обращений пользователей (Excel)"
            );
        } catch (IOException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, "Не получилось создать файл!");
        } catch (TelegramApiException e) {
            assistantTelegramBot.sendReturnedMessage(chatId, "Не получилось отправить файл!");
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.ASSISTANT_TABLE.getCommandText();
    }
}
