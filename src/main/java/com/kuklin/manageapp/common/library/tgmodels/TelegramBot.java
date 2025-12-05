package com.kuklin.manageapp.common.library.tgmodels;

import com.kuklin.manageapp.common.services.AsyncService;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.models.common.Currency;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
public abstract class TelegramBot extends TelegramLongPollingBot implements TelegramBotClient {
    private String botToken;
    public static final String DEFAULT_DELIMETER = " ";
    private final Set<Long> inProcess;

    @Override
    public String getToken() {
        return botToken;
    }

    public TelegramBot(String key) {
        super(key);
        botToken = key;
        inProcess = new HashSet<>();
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
    public void sendVoiceMessage(SendVoice message) throws TelegramApiException {
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

    public void sendDeleteMessage(long chatId, Integer messageId) {
        DeleteMessage deleteMessage = DeleteMessage
                .builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Не получилось удалить сообщение");
        }
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

    public void sendEditMessageReplyMarkupNull(Long chatId, Integer messageId) {
        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId);
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(null);

        try {
            execute(editMarkup);
        } catch (TelegramApiException e) {
            log.error("Не получилось изменить клавиатуру");
        }
    }

    public void notifyAlreadyInProcess(Update update) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.hasPreCheckoutQuery()
                ? update.getPreCheckoutQuery().getFrom().getId()
                : update.getMessage().getChatId();

        sendReturnedMessage(chatId, "Предыдущее сообщение обрабатывается, вам необходимо дождаться его завершения.");
    }

    public void notifyAsyncDone(Update update) {
        if (update.hasCallbackQuery()) {
            return;
        }

        User user = getUserFromUpdate(update);
        if (user == null) {
            log.error("Not a message or callback {} in async done", update);
            return;
        }
        Long tgUserId = user.getId();
        inProcess.remove(tgUserId);
    }

    protected static User getUserFromUpdate(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getFrom();
        } else if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getFrom();
        } else if (update.hasPreCheckoutQuery()) {
            return update.getPreCheckoutQuery().getFrom();
        }

        return null;
    }

    protected boolean doAsync(AsyncService asyncService, Update update, Consumer<Update> runnable) {
        try {
            if (update.hasCallbackQuery()) {
                asyncService.executeAsyncCustom(this, update, runnable);
                return true;
            }

            User user = getUserFromUpdate(update);
            if (user == null) {
                log.error("Not a message or callback {}", update);
                return false;
            }

            Long tgUserId = user.getId();
            if (inProcess.add(tgUserId)) {
                try {
                    asyncService.executeAsyncCustom(this, update, runnable);
                } catch (Exception ex) {
                    inProcess.remove(tgUserId);
                    log.error("Parallel execution failed", ex);
                }
                return true;
            }

            return false;
        } catch (Exception ex) {
            sendReturnedMessage(425120436, ex.getMessage());
            return false;
        }
    }

    public static SendInvoice buildInvoiceOrNull(Long chatId, String title,
                                                 String description, String payload,
                                                 String providerToken, int amount,
                                                 Currency currency, Payment.Provider provider,
                                                 String labelPrice
    ) {

        if (provider.equals(Payment.Provider.STARS) && !currency.equals(Currency.XTR)) {
            return null;
        }

        return SendInvoice.builder()
                .chatId(chatId.toString())
                .title(title)
                .description(description)
                .payload(payload)
                .providerToken(providerToken)
                .currency(currency.name())
                .prices(List.of(new LabeledPrice(labelPrice, amount)))
                .startParameter(provider.getTelegramStartParameter())
                .build();
    }

    public static CreateInvoiceLinkWithTelegramSubscription buildCreateInvoiceLink(
            String title,
            String description, String payload,
            int amount, Currency currency
    ) {
        String TOKEN_DUMMY = "xtr_dummy";

        CreateInvoiceLinkWithTelegramSubscription link = new CreateInvoiceLinkWithTelegramSubscription(2_592_000);
        link.setTitle(title);
        link.setDescription(description);
        link.setPayload(payload);
        link.setCurrency(currency.name());
        link.setPrices(List.of(new LabeledPrice("Подписка 30 дней", amount)));
        link.setProviderToken(TOKEN_DUMMY);

        return link;

    }

    public void answerCallback(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery cq = update.getCallbackQuery();

            AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                    .callbackQueryId(cq.getId())
                    .build();

            try {
                execute(answer);
            } catch (TelegramApiException e) {
                log.error("Callback answer error!");
            }
        }
    }

    public void sendChatActionTyping(Long chatId) {
        SendChatAction typingAction = new SendChatAction();
        typingAction.setChatId(chatId);
        typingAction.setAction(ActionType.TYPING);
        try {
            execute(typingAction);
        } catch (TelegramApiException e) {
            log.error("Не получилось отправить ChatAction!");
        }
    }

    /**
     * Удобный метод для отправки документа из байтов (CSV и т.п.)
     */
    public Message sendDocument(long chatId, byte[] content, String filename, String caption) throws TelegramApiException {
        InputFile inputFile = new InputFile(
                new ByteArrayInputStream(content),
                filename
        );

        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(inputFile)
                .caption(caption)
                .build();

        return execute(sendDocument);
    }
}

