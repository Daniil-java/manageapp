package com.kuklin.manageapp.bots.pomidorotimer.telegram.utils;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
public class BotApiMethodBuilder {
    public static EditMessageText makeEditMessageText(Long chatId, int messageId) {
        String defaultText = "Что-то пошло не так ¯\\_(ツ)_/¯";
        return makeEditMessageText(chatId, messageId, defaultText);
    }
    public static EditMessageText makeEditMessageText(Long chatId, int messageId, String text) {
        return EditMessageText.builder()
                .chatId(chatId)
                .text(text)
                .messageId(messageId)
                .parseMode(ParseMode.HTML)
                .build();
    }

    public static EditMessageText makeEditMessageText(Long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        return EditMessageText.builder()
                .chatId(chatId)
                .text(text)
                .messageId(messageId)
                .replyMarkup(keyboard)
                .parseMode(ParseMode.HTML)
                .build();
    }

    public static SendMessage makeSendMessage(Long chatId) {
        String defaultText = "Что-то пошло не так ¯\\_(ツ)_/¯";
        return makeSendMessage(chatId, defaultText);
    }

    public static SendMessage makeSendMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .build();
    }

    public static SendMessage makeSendMessage(Long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboardMarkup)
                .build();
    }

}
