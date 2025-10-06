package com.kuklin.manageapp.common.library.tgmodels;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;

@Slf4j
public abstract class TelegramBot extends TelegramLongPollingBot implements TelegramBotClient {
    private String botToken;
    public static final String DEFAULT_DELIMETER = "[d]";

    @Override
    public String getToken() {
        return botToken;
    }

    public TelegramBot(String key) {
        super(key);
        botToken = key;
    }

    @Override
    public abstract void onUpdateReceived(Update update);

    @Override
    public void sendMessage(BotApiMethod sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Send message error!", e);
        }
    }

    @Override
    public void sendVoiceMessage(SendVoice message) throws TelegramApiException{
        execute(message);
    }

    @Override
    public Message sendReturnedMessage(SendMessage sendMessage) {
        try {
            return execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Send returned message error!", e);
        }
        return null;
    }

    public Message sendReturnedMessage(long chatId, String text,
                                       ReplyKeyboard replyKeyboard, Integer replyMessageId) {

        SendMessage sendMessage = buildMessage(chatId, text, replyKeyboard, replyMessageId);
        Message message = sendReturnedMessage(sendMessage);
        if (message == null) {
            sendMessage.setParseMode(null);
            sendReturnedMessage(sendMessage);
        }
        return message;
    }

    public Message sendReturnedMessage(long chatId, String text) {
        return sendReturnedMessage(chatId, text, null, null);
    }

    public void sendEditMessage(long chatId, String text,
                                int messageId, InlineKeyboardMarkup inlineKeyboardMarkup) {

        sendMessage(buildEditMessage(chatId, text, messageId, inlineKeyboardMarkup));
    }

    public void sendVoiceMessage(long chatId, byte[] outputAudioFile, String filename)
            throws TelegramApiException {
        String format = ".mp3";
        if (!filename.endsWith(format)) {
            filename = filename.trim() + format;
        }
        SendVoice sendVoice = new SendVoice(
                String.valueOf(chatId),
                new InputFile(new ByteArrayInputStream(outputAudioFile),
                        filename));
        sendVoiceMessage(sendVoice);
    }

    public void editMarkup(long chatId, int messageId, InlineKeyboardMarkup inlineKeyboardMarkup) {
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(inlineKeyboardMarkup);

        sendMessage(editMarkup);
    }

    private SendMessage buildMessage(long chatId, String text,
                                     ReplyKeyboard replyKeyboard, Integer replyMessageId) {
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(replyKeyboard)
                .replyToMessageId(replyMessageId)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .build();
    }

    private EditMessageText buildEditMessage(long chatId, String text, int messageId,
                                             InlineKeyboardMarkup inlineKeyboardMarkup) {
        return EditMessageText.builder()
                .chatId(chatId)
                .text(text)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode(ParseMode.HTML)
                .build();
    }
}
