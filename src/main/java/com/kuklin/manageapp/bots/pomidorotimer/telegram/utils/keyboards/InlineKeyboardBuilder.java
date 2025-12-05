package com.kuklin.manageapp.bots.pomidorotimer.telegram.utils.keyboards;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InlineKeyboardBuilder {

    public static InlineKeyboardMarkup buildKeyboard(List<List<InlineKeyboardButton>> buttons) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        inlineKeyboardMarkup.setKeyboard(buttons);
        return inlineKeyboardMarkup;
    }

    public static InlineKeyboardMarkup getTrueOrFalseK() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton trueButton = new InlineKeyboardButton("true");
        InlineKeyboardButton falseButton = new InlineKeyboardButton("false");

        trueButton.setCallbackData("true");
        falseButton.setCallbackData("false");

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();
        rowList.add(Arrays.asList(trueButton, falseButton));

        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }

}
