package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

final class LegalLinkKeyboards {
    private LegalLinkKeyboards() {
    }

    /** Одна кнопка-ссылка: открывает страницу в браузере Telegram. */
    static InlineKeyboardMarkup buildLinkKeyboard(String text, String url) {
        return TelegramKeyboard.builder().row(TelegramKeyboard.urlButton(text, url)).build();
    }
}
