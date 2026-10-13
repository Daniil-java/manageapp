package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

final class LegalLinkKeyboards {
    private LegalLinkKeyboards() {
    }

    /** Одна кнопка-ссылка: открывает страницу в браузере Telegram. */
    static InlineKeyboardMarkup buildLinkKeyboard(String text, String url) {
        InlineKeyboardButton linkButton = new InlineKeyboardButton();
        linkButton.setText(text);
        linkButton.setUrl(url);
        return TelegramKeyboard.builder().row(linkButton).build();
    }
}
