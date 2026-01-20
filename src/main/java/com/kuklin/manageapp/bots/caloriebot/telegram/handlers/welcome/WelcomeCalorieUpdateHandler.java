package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.welcome;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WelcomeCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private static final String INSTR_URL = "https://kuklin.dev/calorie/instruction";
    private static final String TEXT_MSG = "📖 Инструкция";

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
                TEXT_MSG,
                buildInstructionKeyboard(),
                null
        );
    }

    private void processCallbackQuery(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                TEXT_MSG,
                update.getCallbackQuery().getMessage().getMessageId(),
                buildInstructionKeyboard()
        );
    }

    private InlineKeyboardMarkup buildInstructionKeyboard() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("📖 Инструкция в miniApp");
        button.setWebApp(new WebAppInfo(INSTR_URL));

        InlineKeyboardButton close = new InlineKeyboardButton();
        close.setText("Закрыть");
        close.setCallbackData(Command.CALORIE_CLOSE.getCommandText());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(button), List.of(close)));

        return markup;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WELCOME.getCommandText();
    }
}
