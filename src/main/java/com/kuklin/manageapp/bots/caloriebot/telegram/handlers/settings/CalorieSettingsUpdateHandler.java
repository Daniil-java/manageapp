package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

/**
 * Обработчик для комманды настроек
 */
@Component
@RequiredArgsConstructor
public class CalorieSettingsUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSettingsService userSettingsService;
    //Собираем в лист, все реализации интерфеса, который отвечает за параметр настройки
    private final List<CalorieSettingsHandler> calorieSettingsHandlers;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
            return;
        }

        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                userSettingsService.getOrCreate(telegramUser.getTelegramId()).toPrettyText(),
                getSettingsKeyboard(),
                null
        );
    }


    /**
     * Обработчик колбэка
     */
    private void processCallback(Update update, TelegramUser telegramUser) {
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                userSettingsService.getOrCreate(telegramUser.getTelegramId()).toPrettyText(),
                update.getCallbackQuery().getMessage().getMessageId(),
                getSettingsKeyboard()
        );
    }

    /**
     * Клавиатура для сообщения настроек
     */
    private InlineKeyboardMarkup getSettingsKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (CalorieSettingsHandler settingsHandler: calorieSettingsHandlers) {
            builder.row(
                    TelegramKeyboard.button(
                            settingsHandler.getLabel(), settingsHandler.getHandlerListName()
                    )
            );
        }
        builder.row(TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText()));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS.getCommandText();
    }
}
