package com.kuklin.manageapp.common.library.tgmodels;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public interface TelegramBotClient {

    void sendMessage(BotApiMethod sendMessage);

    void sendVoiceMessage(SendVoice message) throws TelegramApiException;

    Message sendReturnedMessage(SendMessage sendMessage);

    String getToken();
    BotIdentifier getBotIdentifier();
}
