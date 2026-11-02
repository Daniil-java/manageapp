package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.startflow;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieLanguageSwitcher;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

/**
 * Ответ на вопрос о языке из /start: колбэк «/startlang en».
 * Сохраняет язык, вопрос превращает в «🇬🇧 English — great, let's go!» и продолжает /start на выбранном языке.
 */
@Component
@RequiredArgsConstructor
public class StartLanguageUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final StartUpdateHandler startUpdateHandler;
    private final CalorieLanguageSwitcher languageSwitcher;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        String[] parts = query.getData().split(TelegramBot.DEFAULT_DELIMETER);
        Optional<AppLanguage> chosen = AppLanguage.fromCode(parts.length > 1 ? parts[1] : null);
        if (chosen.isEmpty()) return;

        Long chatId = query.getMessage().getChatId();
        Long appUserId = telegramUser.getAppUserId();
        AppLanguage language = chosen.get();
        // Второе нажатие или старый вопрос — /start уже продолжен: приветствие не повторяем, только меняем язык
        boolean alreadyChosen = texts.hasChosenLanguage(appUserId);
        if (alreadyChosen) {
            languageSwitcher.switchLanguage(chatId, appUserId, language);
        } else {
            texts.setLanguage(appUserId, language);
        }

        calorieTelegramBot.sendEditMessage(
                chatId,
                texts.get(language, BotTextKeys.LANGUAGE_CHOSEN, language.getLabel()),
                query.getMessage().getMessageId(),
                null
        );
        if (!alreadyChosen) {
            startUpdateHandler.continueStart(chatId, appUserId);
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START_LANGUAGE.getCommandText();
    }
}
