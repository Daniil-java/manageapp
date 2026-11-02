package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Смена языка пользователя — из настроек бота, /lang и мини-приложения.
 * Сохраняет язык и, если язык бота от этого поменялся, присылает нижнюю клавиатуру на новом языке:
 * иначе её подписи висели бы на старом языке до следующего /start.
 */
@Component
@RequiredArgsConstructor
public class CalorieLanguageSwitcher {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;
    private final TelegramUserService telegramUserService;

    /** Из бота — чат известен. */
    public void switchLanguage(Long chatId, Long appUserId, AppLanguage language) {
        if (save(appUserId, language)) {
            sendMenu(chatId, appUserId, language);
        }
    }

    /** Из мини-приложения — клавиатуру шлём в чат с ботом, если он у пользователя есть (с сайта может и не быть). */
    public void switchLanguage(Long appUserId, AppLanguage language) {
        if (!save(appUserId, language)) return;
        telegramUserService.findByAppUserIdAndBotIdentifier(appUserId, BotIdentifier.CALORIE_BOT)
                .ifPresent(user -> sendMenu(user.getTelegramId(), appUserId, language));
    }

    /** Сохраняет выбор; true — язык бота сменился (раньше был другой выбранный или язык по умолчанию). */
    private boolean save(Long appUserId, AppLanguage language) {
        boolean changed = texts.languageOf(appUserId) != language;
        texts.setLanguage(appUserId, language);
        return changed;
    }

    private void sendMenu(Long chatId, Long appUserId, AppLanguage language) {
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(language, BotTextKeys.LANGUAGE_CHANGED, language.getLabel()),
                menuButtons.buildKeyboard(appUserId),
                null
        );
    }
}
