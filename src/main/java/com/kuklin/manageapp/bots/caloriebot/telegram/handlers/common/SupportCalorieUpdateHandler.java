package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

@RequiredArgsConstructor
@Component
@Slf4j
public class SupportCalorieUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    private static final String SUPPORT_URL = "tg://user?id=7666281607";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                // ID строкой: число ICU напечатал бы с разделителями разрядов (425 120 436)
                texts.get(appUserId, BotTextKeys.SUPPORT_TEXT, String.valueOf(telegramUser.getTelegramId())),
                getKeyboard(appUserId),
                null
        );

    }

    private InlineKeyboardMarkup getKeyboard(Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder keyboard =
                new TelegramKeyboard.TelegramKeyboardBuilder();

        InlineKeyboardButton linkButton = new InlineKeyboardButton();
        linkButton.setText(texts.get(appUserId, BotTextKeys.SUPPORT_BUTTON));
        linkButton.setUrl(SUPPORT_URL);

        keyboard.row(TelegramKeyboard.withStyle(linkButton, ButtonStyle.PRIMARY));
        return keyboard.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SUPPORT.getCommandText();
    }
}
