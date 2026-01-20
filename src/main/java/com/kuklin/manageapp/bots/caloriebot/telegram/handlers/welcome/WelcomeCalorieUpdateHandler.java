package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.welcome;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WelcomeCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update, telegramUser);
        } else if (update.hasCallbackQuery()) {
            processCallbackQuery(update, telegramUser);
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                "Инструкция",
                buildInstructionKeyboard(),
                null
        );
    }

    private InlineKeyboardMarkup buildInstructionKeyboard() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("📖 Инструкция");

        String url = "https://kuklin.dev/calorie/instruction";

        button.setWebApp(new WebAppInfo(url));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button)));

        return markup;
    }

    private void processCallbackQuery(Update update, TelegramUser telegramUser) {
        return;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WELCOME.getCommandText();
    }
}
