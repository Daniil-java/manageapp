package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /lang — смена языка для разработчиков (calorie.i18n.dev-telegram-ids), пользователям пока не открыта.
 * /lang — текущий язык, /lang en — переключить, /lang reset — вернуть язык по умолчанию.
 */
@Component
@RequiredArgsConstructor
public class LanguageDevUpdateHandler implements CalorieBotUpdateHandler {
    private static final String RESET = "reset";

    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTextsService texts;
    private final I18nProperties i18nProperties;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!i18nProperties.getDevTelegramIds().contains(telegramUser.getTelegramId())) return;

        Long userId = telegramUser.getAppUserId();
        String[] parts = update.getMessage().getText().trim().split("\\s+");
        String arg = parts.length > 1 ? parts[1] : null;

        String reply;
        if (arg == null) {
            String key = texts.hasChosenLanguage(userId)
                    ? BotTextKeys.DEV_LANG_CURRENT
                    : BotTextKeys.DEV_LANG_CURRENT_DEFAULT;
            reply = texts.get(userId, key, describe(texts.languageOf(userId)), availableLanguages());
        } else if (RESET.equalsIgnoreCase(arg)) {
            texts.setLanguage(userId, null);
            reply = texts.get(userId, BotTextKeys.DEV_LANG_RESET, describe(texts.languageOf(userId)));
        } else {
            Optional<AppLanguage> language = AppLanguage.fromCode(arg);
            if (language.isEmpty()) {
                reply = texts.get(userId, BotTextKeys.DEV_LANG_UNKNOWN, arg, availableLanguages());
            } else {
                texts.setLanguage(userId, language.get());
                reply = texts.get(userId, BotTextKeys.DEV_LANG_CHANGED, describe(language.get()));
            }
        }

        calorieTelegramBot.sendReturnedMessage(telegramUser.getTelegramId(), reply);
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
