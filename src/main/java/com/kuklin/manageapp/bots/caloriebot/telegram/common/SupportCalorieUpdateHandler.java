package com.kuklin.manageapp.bots.caloriebot.telegram.common;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
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

    private static final String MSG =
            """
                    Если у вас возникли вопросы или проблемы — свяжитесь с нашей поддержкой.
                                        
                    Пожалуйста, включите следующие информацию в сообщение:
                    User ID: %s
                                        
                    Нажмите кнопку ниже, чтобы перейти в чат с поддержкой. 👇
                    """;
    private static final String SUPPORT_URL = "tg://user?id=7666281607";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                String.format(MSG, telegramUser.getTelegramId()),
                getKeyboard(),
                null
        );

    }

    private InlineKeyboardMarkup getKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder keyboard =
                new TelegramKeyboard.TelegramKeyboardBuilder();

        InlineKeyboardButton linkButton = new InlineKeyboardButton();
        linkButton.setText("Написать в поддержку");
        linkButton.setUrl(SUPPORT_URL);

        keyboard.row(linkButton);
        return keyboard.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SUPPORT.getCommandText();
    }
}
