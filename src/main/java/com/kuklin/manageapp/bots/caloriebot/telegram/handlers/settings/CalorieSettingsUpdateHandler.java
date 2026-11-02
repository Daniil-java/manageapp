package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.SettingsTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CalorieSettingsUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSettingsService userSettingsService;
    private final CalorieTexts texts;
    private final SettingsTexts settingsTexts;
    private final CalorieMenuButtons menuButtons;
    //Собираем в лист, все реализации интерфеса, который отвечает за параметр настройки
    private final List<CalorieSettingsHandler> calorieSettingsHandlers;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
            return;
        }

        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                settingsTexts.buildSettingsText(userSettingsService.getOrCreate(telegramUser.getAppUserId()), language),
                getSettingsKeyboard(language),
                null
        );
    }


    /**
     * Обработчик колбэка
     */
    private void processCallback(Update update, TelegramUser telegramUser) {
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                settingsTexts.buildSettingsText(userSettingsService.getOrCreate(telegramUser.getAppUserId()), language),
                update.getCallbackQuery().getMessage().getMessageId(),
                getSettingsKeyboard(language)
        );
    }

    /**
     * Клавиатура для сообщения настроек
     */
    private InlineKeyboardMarkup getSettingsKeyboard(AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (CalorieSettingsHandler settingsHandler: calorieSettingsHandlers) {
            builder.row(
                    TelegramKeyboard.button(
                            texts.get(language, settingsHandler.getLabelKey()), settingsHandler.getHandlerListName()
                    )
            );
        }
        builder.row(menuButtons.buildBackToMenuButton(language));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS.getCommandText();
    }
}
