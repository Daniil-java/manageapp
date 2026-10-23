package com.kuklin.manageapp.common.library.tgutils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Цвет кнопки на telegrambots 6.9.7.1. ObjectMapper — как в библиотеке (DefaultAbsSender, PartialBotApiMethod): new ObjectMapper().
 */
class StyledInlineKeyboardButtonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** style уходит в запросе; у обычной кнопки поля нет вовсе. */
    @Test
    void styleIsSentInRequest() throws Exception {
        SendMessage request = SendMessage.builder()
                .chatId(1L)
                .text("x")
                .replyMarkup(TelegramKeyboard.builder()
                        .row(TelegramKeyboard.button("🗑 Удалить", "/deleteDish 1", ButtonStyle.DANGER),
                                TelegramKeyboard.button("⭐ В избранное", "/favoriteadd 1"))
                        .build())
                .build();

        JsonNode row = mapper.readTree(mapper.writeValueAsString(request)).at("/reply_markup/inline_keyboard/0");

        assertThat(row.get(0).get("style").asText()).isEqualTo("danger");
        assertThat(row.get(0).get("callback_data").asText()).isEqualTo("/deleteDish 1");
        assertThat(row.get(1).has("style")).isFalse();
    }

    /** Telegram присылает клавиатуру со style обратно (нажатие кнопки) — библиотека её читает, лишнее поле игнорирует. */
    @Test
    void updateWithStyleIsRead() throws Exception {
        String json = """
                {"update_id": 1, "callback_query": {"id": "q", "from": {"id": 5, "is_bot": false, "first_name": "A"},
                  "chat_instance": "c", "data": "/scale 1 10",
                  "message": {"message_id": 7, "date": 1761000000, "chat": {"id": 5, "type": "private"}, "text": "x",
                    "reply_markup": {"inline_keyboard": [[{"text": "🗑 Удалить", "callback_data": "/deleteDish 1", "style": "danger"}]]}}}}
                """;

        Update update = mapper.readValue(json, Update.class);
        Message message = (Message) update.getCallbackQuery().getMessage();

        assertThat(message.getReplyMarkup().getKeyboard().get(0).get(0).getCallbackData()).isEqualTo("/deleteDish 1");
    }

    /** withStyle сохраняет остальные поля кнопки — url, webApp. */
    @Test
    void withStyleKeepsOtherFields() {
        InlineKeyboardButton share = InlineKeyboardButton.builder().text("🚀").url("https://t.me/share/url?url=x").build();
        InlineKeyboardButton app = InlineKeyboardButton.builder().text("📖").webApp(new WebAppInfo("https://zefir.fit")).build();

        StyledInlineKeyboardButton styledShare = (StyledInlineKeyboardButton) TelegramKeyboard.withStyle(share, ButtonStyle.PRIMARY);
        StyledInlineKeyboardButton styledApp = (StyledInlineKeyboardButton) TelegramKeyboard.withStyle(app, ButtonStyle.SUCCESS);

        assertThat(styledShare.getUrl()).isEqualTo("https://t.me/share/url?url=x");
        assertThat(styledShare.getStyle()).isEqualTo("primary");
        assertThat(styledApp.getWebApp().getUrl()).isEqualTo("https://zefir.fit");
        assertThat(styledApp.getStyle()).isEqualTo("success");
    }

    @Test
    void markupValidates() {
        InlineKeyboardMarkup markup = TelegramKeyboard.builder()
                .row(TelegramKeyboard.button("✅ Сохранить", "/profileedit AGE SET 30", ButtonStyle.SUCCESS))
                .build();

        org.assertj.core.api.Assertions.assertThatCode(markup::validate).doesNotThrowAnyException();
    }
}
