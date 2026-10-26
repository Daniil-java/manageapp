package com.kuklin.manageapp.common.library.tgutils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

/**
 * Инлайн-кнопка с цветом (поле style из Bot API 9.4).
 * <p>
 * В telegrambots 6.9.7.1 этого поля нет. Jackson сериализует кнопку по её настоящему классу,
 * поэтому style уходит в запросе, а пустой (null) — не уходит вовсе (@JsonInclude(NON_NULL) у BotApiObject).
 * После обновления библиотеки класс заменяется родным полем, вызовы TelegramKeyboard.button(..., style) не меняются.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class StyledInlineKeyboardButton extends InlineKeyboardButton {

    @JsonProperty("style")
    private String style;

    /** Копия кнопки с цветом: текст, callback, url, webApp и остальные поля — как у исходной. */
    public static StyledInlineKeyboardButton of(InlineKeyboardButton source, ButtonStyle style) {
        StyledInlineKeyboardButton button = new StyledInlineKeyboardButton();
        button.setText(source.getText());
        button.setUrl(source.getUrl());
        button.setCallbackData(source.getCallbackData());
        button.setCallbackGame(source.getCallbackGame());
        button.setSwitchInlineQuery(source.getSwitchInlineQuery());
        button.setSwitchInlineQueryCurrentChat(source.getSwitchInlineQueryCurrentChat());
        button.setPay(source.getPay());
        button.setLoginUrl(source.getLoginUrl());
        button.setWebApp(source.getWebApp());
        button.setStyle(style.getApiValue());
        return button;
    }
}
