package com.kuklin.manageapp.common.telegram;

import com.kuklin.manageapp.common.entities.TelegramUser;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface UpdateHandler {
    void handle(Update update, TelegramUser telegramUser);
    String getHandlerListName();

}
