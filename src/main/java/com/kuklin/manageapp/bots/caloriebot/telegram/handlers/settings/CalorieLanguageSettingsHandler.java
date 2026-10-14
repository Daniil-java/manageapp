package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieLanguageSwitcher;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * ⚙ Настройки → 🌐 Язык: список языков, текущий отмечен ✅.
 * Колбэк «langset» — показать список, «langset SET en» — выбрать язык.
 */
@Component
@RequiredArgsConstructor
public class CalorieLanguageSettingsHandler implements CalorieSettingsHandler {
    private static final String SET_CMD = "SET";

    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final CalorieLanguageSwitcher languageSwitcher;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Long chatId = query.getMessage().getChatId();
        Long appUserId = telegramUser.getAppUserId();

        findChosenLanguage(query.getData())
                .ifPresent(language -> languageSwitcher.switchLanguage(chatId, appUserId, language));

        // Список заново — уже на новом языке и с ✅ на выбранном
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendEditMessage(
                chatId,
                texts.get(language, BotTextKeys.LANGUAGE_SETTINGS_TITLE),
                query.getMessage().getMessageId(),
                buildLanguageKeyboard(language, true)
        );
    }

    /** Список языков новым сообщением — для /lang. */
    public void sendLanguageChoice(Long chatId, Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(language, BotTextKeys.LANGUAGE_SETTINGS_TITLE),
                buildLanguageKeyboard(language, false),
                null
        );
    }

    /** «langset SET en» → EN; просто «langset» или неизвестный код — empty. */
    private static Optional<AppLanguage> findChosenLanguage(String data) {
        String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
        if (parts.length < 3 || !SET_CMD.equals(parts[1])) return Optional.empty();
        return AppLanguage.fromCode(parts[2]);
    }

    private InlineKeyboardMarkup buildLanguageKeyboard(AppLanguage current, boolean withBackToSettings) {
        List<InlineKeyboardButton> languages = Arrays.stream(AppLanguage.values())
                .map(language -> TelegramKeyboard.button(
                        language == current ? "✅ " + language.getLabel() : language.getLabel(),
                        getHandlerListName()
                                + TelegramBot.DEFAULT_DELIMETER + SET_CMD
                                + TelegramBot.DEFAULT_DELIMETER + language.getCode()))
                .toList();

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder().row(languages);
        builder.row(withBackToSettings
                ? TelegramKeyboard.button(texts.get(current, BotTextKeys.SETTINGS_BUTTON_BACK_TO_SETTINGS),
                        Command.CALORIE_SETTINGS.getCommandText())
                : TelegramKeyboard.button(texts.get(current, BotTextKeys.COMMON_BUTTON_CLOSE),
                        Command.CALORIE_CLOSE.getCommandText()));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS_LANGUAGE.getCommandText();
    }

    @Override
    public String getLabelKey() {
        return BotTextKeys.SETTINGS_BUTTON_LANGUAGE;
    }
}
