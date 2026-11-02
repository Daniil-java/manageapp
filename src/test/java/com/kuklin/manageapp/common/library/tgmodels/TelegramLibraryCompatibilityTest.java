package com.kuklin.manageapp.common.library.tgmodels;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Что проект ждёт от библиотеки Telegram (проверено при переходе 6.9.0 → 6.9.7.1).
 * Упал после обновления библиотеки — сначала смотри сюда: запросы или разбор апдейтов поменялись.
 */
class TelegramLibraryCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deprecatedSendParamsAreStillSentAsBefore() throws Exception {
        // в Bot API 7.0 заменены на link_preview_options / reply_parameters, но проект шлёт старые — как в 6.9.0
        SendMessage send = SendMessage.builder().chatId("1").text("hi").parseMode("HTML")
                .disableWebPagePreview(true).replyToMessageId(5).build();
        EditMessageText edit = EditMessageText.builder().chatId("1").messageId(2).text("t")
                .disableWebPagePreview(true).build();

        assertThat(objectMapper.writeValueAsString(send))
                .isEqualTo("{\"chat_id\":\"1\",\"text\":\"hi\",\"parse_mode\":\"HTML\",\"disable_web_page_preview\":true,"
                        + "\"reply_to_message_id\":5,\"method\":\"sendmessage\"}");
        assertThat(objectMapper.writeValueAsString(edit)).contains("\"disable_web_page_preview\":true");
        assertThatCode(send::validate).doesNotThrowAnyException();
        assertThatCode(edit::validate).doesNotThrowAnyException();
    }

    @Test
    void callbackUnderOldOrDeletedMessageIsInaccessibleButKeepsChatAndId() throws Exception {
        Update update = objectMapper.readValue("""
                {"update_id":1,"callback_query":{"id":"c","from":{"id":7,"is_bot":false,"first_name":"A"},
                 "chat_instance":"x","data":"d","message":{"message_id":10,"date":0,"chat":{"id":7,"type":"private"}}}}
                """, Update.class);

        assertThat(update.getCallbackQuery().getMessage()).isInstanceOf(InaccessibleMessage.class);
        assertThat(update.getCallbackQuery().getMessage().getChatId()).isEqualTo(7L);
        assertThat(TelegramBot.getCallbackMessage(update.getCallbackQuery()).getMessageId()).isEqualTo(10);
    }

    @Test
    void regularUpdatesParseAsBefore() throws Exception {
        Update callback = objectMapper.readValue("""
                {"update_id":2,"callback_query":{"id":"c","from":{"id":7,"is_bot":false,"first_name":"A"},
                 "chat_instance":"x","data":"d","message":{"message_id":11,"date":1700000000,
                 "chat":{"id":7,"type":"private"},"text":"hello",
                 "reply_to_message":{"message_id":3,"date":1700000000,"chat":{"id":7,"type":"private"},"text":"orig"}}}}
                """, Update.class);
        Update payment = objectMapper.readValue("""
                {"update_id":3,"message":{"message_id":13,"date":1700000000,"chat":{"id":7,"type":"private"},
                 "from":{"id":7,"is_bot":false,"first_name":"A"},
                 "successful_payment":{"currency":"XTR","total_amount":100,"invoice_payload":"pl",
                 "telegram_payment_charge_id":"t","provider_payment_charge_id":"p"}}}
                """, Update.class);
        Update reactions = objectMapper.readValue("""
                {"update_id":4,"message_reaction_count":{"chat":{"id":-100,"type":"channel","title":"c"},
                 "message_id":5,"date":1700000000,
                 "reactions":[{"type":{"type":"emoji","emoji":"👍"},"total_count":3}]}}
                """, Update.class);

        Message message = (Message) callback.getCallbackQuery().getMessage();
        assertThat(message.getText()).isEqualTo("hello");
        assertThat(message.getReplyToMessage().getText()).isEqualTo("orig");
        assertThat(payment.getMessage().getSuccessfulPayment().getTelegramPaymentChargeId()).isEqualTo("t");
        assertThat(reactions.getMessageReactionCount().getReactions().get(0).getTotalCount()).isEqualTo(3);
    }
}
