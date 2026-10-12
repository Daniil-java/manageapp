package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Кнопки главного меню на языке пользователя.
 * <p>
 * Текст команды (Command.getCommandText) остаётся внутренним ключом: по нему фасад ищет обработчик,
 * он же уходит в callbackData. Пользователь видит подпись из словаря bot_*.
 * Нажатие кнопки нижней клавиатуры приходит обычным сообщением с подписью — findCommand() по ней находит команду
 * на любом языке, так что работают и старые клавиатуры, оставшиеся в чатах после смены языка.
 */
@Component
public class CalorieMenuButtons {

    @Getter
    @RequiredArgsConstructor
    public enum Button {
        TODAY(BotTextKeys.BUTTON_TODAY, Command.CALORIE_TODAY_LIST),
        WATER(BotTextKeys.BUTTON_WATER, Command.CALORIE_WATER),
        FAVORITES(BotTextKeys.BUTTON_FAVORITES, Command.CALORIE_FAVORITE),
        SETTINGS(BotTextKeys.BUTTON_SETTINGS, Command.CALORIE_SETTINGS),
        PROFILE(BotTextKeys.BUTTON_PROFILE, Command.CALORIE_PROFILE),
        REPORT(BotTextKeys.BUTTON_REPORT, Command.CALORIE_REPORT),
        FAQ(BotTextKeys.BUTTON_FAQ, Command.CALORIE_WELCOME),
        SUBSCRIPTION(BotTextKeys.BUTTON_SUBSCRIPTION, Command.CALORIE_PAYMENT_PAYLOAD_PLAN),
        INVITE(BotTextKeys.BUTTON_INVITE, Command.CALORIE_UTM);

        private final String textKey;
        private final Command command;
    }

    /** Порядок кнопок в меню — одинаковый у нижней клавиатуры и у кнопок в сообщении /menu. */
    private static final List<List<Button>> LAYOUT = List.of(
            List.of(Button.TODAY),
            List.of(Button.WATER, Button.FAVORITES),
            List.of(Button.SETTINGS, Button.PROFILE),
            List.of(Button.REPORT, Button.FAQ),
            List.of(Button.SUBSCRIPTION, Button.INVITE)
    );

    private final CalorieTextsService texts;
    /** Подпись на любом языке (и старый текст команды) → команда. */
    private final Map<String, Command> commandsByLabel;

    public CalorieMenuButtons(CalorieTextsService texts) {
        this.texts = texts;
        this.commandsByLabel = buildCommandsByLabel(texts);
    }

    public String getLabel(Button button, AppLanguage language) {
        return texts.get(language, button.getTextKey());
    }

    /** Команда кнопки по тексту нажатия; empty — это не кнопка меню. */
    public Optional<Command> findCommand(String text) {
        if (text == null) return Optional.empty();
        return Optional.ofNullable(commandsByLabel.get(text.strip()));
    }

    /** Нижняя клавиатура бота на языке пользователя. */
    public ReplyKeyboardMarkup buildKeyboard(Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(LAYOUT.stream().map(row -> {
            KeyboardRow keyboardRow = new KeyboardRow();
            row.forEach(button -> keyboardRow.add(getLabel(button, language)));
            return keyboardRow;
        }).toList());
        return markup;
    }

    /** То же меню кнопками под сообщением: подпись на языке пользователя, в callbackData — команда. */
    public InlineKeyboardMarkup buildInlineMenu(Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        return new InlineKeyboardMarkup(LAYOUT.stream().map(row -> row.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(getLabel(button, language))
                        .callbackData(button.getCommand().getCommandText())
                        .build())
                .toList()).toList());
    }

    private static Map<String, Command> buildCommandsByLabel(CalorieTextsService texts) {
        Map<String, Command> map = new HashMap<>();
        for (Button button : Button.values()) {
            putUnique(map, button.getCommand().getCommandText(), button.getCommand());
            for (AppLanguage language : AppLanguage.values()) {
                putUnique(map, texts.get(language, button.getTextKey()).strip(), button.getCommand());
            }
        }
        return Map.copyOf(map);
    }

    /** Одна подпись у двух разных команд — бот не поймёт, какую кнопку нажали: падаем на старте. */
    private static void putUnique(Map<String, Command> map, String label, Command command) {
        Command previous = map.put(label, command);
        if (previous != null && previous != command) {
            throw new IllegalStateException("Button label '%s' is used by %s and %s".formatted(label, previous, command));
        }
    }
}
