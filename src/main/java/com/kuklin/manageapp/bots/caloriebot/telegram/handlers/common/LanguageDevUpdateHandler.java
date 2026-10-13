package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieLanguageSwitcher;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings.CalorieLanguageSettingsHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /lang — список языков (как ⚙ Настройки → 🌐 Язык), для всех.
 * Разработчикам (calorie.i18n.dev-telegram-ids) ещё /lang en — переключить сразу,
 * /lang reset — сбросить выбор, чтобы /start снова спросил язык.
 */
@Component
@RequiredArgsConstructor
public class LanguageDevUpdateHandler implements CalorieBotUpdateHandler {
    private static final String RESET = "reset";

    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final I18nProperties i18nProperties;
    private final CalorieLanguageSettingsHandler languageSettingsHandler;
    private final CalorieLanguageSwitcher languageSwitcher;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        Long userId = telegramUser.getAppUserId();
        String[] parts = update.getMessage().getText().trim().split("\\s+");
        String arg = parts.length > 1 ? parts[1] : null;

        if (arg == null || !i18nProperties.getDevTelegramIds().contains(telegramUser.getTelegramId())) {
            languageSettingsHandler.sendLanguageChoice(chatId, userId);
            return;
        }

        if (RESET.equalsIgnoreCase(arg)) {
            texts.setLanguage(userId, null);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(userId, BotTextKeys.DEV_LANG_RESET, describe(texts.languageOf(userId))));
            return;
        }

        Optional<AppLanguage> language = AppLanguage.fromCode(arg);
        if (language.isEmpty()) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(userId, BotTextKeys.DEV_LANG_UNKNOWN, arg, availableLanguages()));
            return;
        }
        languageSwitcher.switchLanguage(chatId, userId, language.get());
    }

    private static String describe(AppLanguage language) {
        return language.getNativeName() + " (" + language.getCode() + ")";
    }

    private static String availableLanguages() {
        return Arrays.stream(AppLanguage.values())
                .map(AppLanguage::getCode)
                .collect(Collectors.joining(", "));
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_LANGUAGE.getCommandText();
    }
}
