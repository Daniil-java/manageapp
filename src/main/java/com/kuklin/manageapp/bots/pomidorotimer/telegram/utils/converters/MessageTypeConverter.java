package com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.converters;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public class MessageTypeConverter {

    public static SendMessage convertEditToSend(EditMessageText editMessageText) {
        return SendMessage.builder()
                .chatId(editMessageText.getChatId())
                .text(editMessageText.getText())
                .replyMarkup(editMessageText.getReplyMarkup())
                .parseMode(editMessageText.getParseMode())
                .build();
    }

    public static EditMessageText convertSendToEdit(SendMessage sendMessage) {
        EditMessageText editMessageText =EditMessageText.builder()
                .chatId(sendMessage.getChatId())
                .text(sendMessage.getText())
                .parseMode(sendMessage.getParseMode()).build();
        if (sendMessage.getReplyMarkup() instanceof InlineKeyboardMarkup) {
            editMessageText.setReplyMarkup((InlineKeyboardMarkup) sendMessage.getReplyMarkup());
        }

        return editMessageText;
    }
}
