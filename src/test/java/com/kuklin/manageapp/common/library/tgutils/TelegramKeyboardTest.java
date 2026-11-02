package com.kuklin.manageapp.common.library.tgutils;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramKeyboardTest {

    @Test
    void urlAndWebAppButtons() {
        InlineKeyboardButton link = TelegramKeyboard.urlButton("Открыть", "https://zefir.fit");
        InlineKeyboardButton app = TelegramKeyboard.webAppButton("📖", "https://zefir.fit/app", ButtonStyle.PRIMARY);

        assertThat(link.getUrl()).isEqualTo("https://zefir.fit");
        assertThat(link.getCallbackData()).isNull();
        assertThat(app.getWebApp().getUrl()).isEqualTo("https://zefir.fit/app");
        assertThat(((StyledInlineKeyboardButton) app).getStyle()).isEqualTo("primary");
    }

    /** Кнопка-подпись и заглушка шлют NO_ACTION — значение «temp» из старых сообщений. */
    @Test
    void noActionAndPlaceholder() {
        assertThat(TelegramKeyboard.NO_ACTION).isEqualTo("temp");
        assertThat(TelegramKeyboard.noActionButton("150 г").getCallbackData()).isEqualTo("temp");
        assertThat(TelegramKeyboard.placeholderButton().getText()).isEqualTo("·");
        assertThat(TelegramKeyboard.placeholderButton().getCallbackData()).isEqualTo("temp");
    }

    /** Пустой ряд не попадает в клавиатуру — необязательные кнопки без проверок у вызывающего. */
    @Test
    void emptyRowsAreSkipped() {
        InlineKeyboardMarkup markup = TelegramKeyboard.builder()
                .row(TelegramKeyboard.button("a", "1"))
                .row(List.of())
                .row(new InlineKeyboardButton[0])
                .row(TelegramKeyboard.button("b", "2"))
                .build();

        assertThat(markup.getKeyboard()).hasSize(2);
    }
}
