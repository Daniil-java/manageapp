package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Кнопки главного меню на языке пользователя.
 * <p>
 * Текст команды (Command.getCommandText) остаётся внутренним ключом: по нему фасад ищет обработчик,
 * он же уходит в callbackData. Пользователь видит подпись из словаря bot_*.
 * Нажатие кнопки нижней клавиатуры приходит обычным сообщением с подписью — findCommand() по ней находит команду
 * на любом языке, так что работают и старые клавиатуры, оставшиеся в чатах после смены языка.
 * Подписи — только для показа: тексты команд не меняются, они префиксы в callbackData (через пробел).
 * <p>
 * Навигация без состояния: у экрана раздела внизу всегда «← Меню» — она превращает это сообщение в меню,
 * «Закрыть» — только у самого меню. Открыт ли раздел из меню, видно по клавиатуре сообщения (isMenuKeyboard).
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
        INVITE(BotTextKeys.BUTTON_INVITE, Command.CALORIE_UTM),
        MENU(BotTextKeys.BUTTON_MENU, Command.CALORIE_MENU);

        private final String textKey;
        private final Command command;
    }

    /**
     * Нижняя клавиатура — только частые действия, остальное в «Меню».
     * Убранные отсюда кнопки остаются в Button: старые клавиатуры в чатах по-прежнему работают.
     */
    private static final List<List<Button>> KEYBOARD_LAYOUT = List.of(
            List.of(Button.TODAY),
            List.of(Button.WATER, Button.FAVORITES),
            List.of(Button.REPORT, Button.MENU)
    );

    /** Полное меню кнопками в сообщении /menu. */
    private static final List<List<Button>> INLINE_MENU_LAYOUT = List.of(
            List.of(Button.TODAY),
            List.of(Button.WATER, Button.FAVORITES),
            List.of(Button.SETTINGS, Button.PROFILE),
            List.of(Button.REPORT, Button.FAQ),
            List.of(Button.SUBSCRIPTION, Button.INVITE)
    );

    /** callbackData кнопок меню — по ним узнаём сообщение с меню. */
    private static final Set<String> INLINE_MENU_CALLBACKS = INLINE_MENU_LAYOUT.stream()
            .flatMap(List::stream)
            .map(button -> button.getCommand().getCommandText())
            .collect(Collectors.toUnmodifiableSet());

    /** «← Меню»: MenuCalorieUpdateHandler показывает меню в этом же сообщении. Обычный /menu присылает новое. */
    public static final String BACK_TO_MENU_DATA = Command.CALORIE_MENU.getCommandText() + TelegramBot.DEFAULT_DELIMETER + "back";

    /**
     * Прежние английские подписи — нажатия из клавиатур, оставшихся в чатах до смены стиля.
     * Прежние русские подписи совпадают с текстами команд и распознаются без этого списка.
     */
    private static final Map<String, Button> LEGACY_LABELS = Map.of(
            "📊TODAY", Button.TODAY,
            "🌟 FAVORITE DISHES", Button.FAVORITES,
            "⚙Settings", Button.SETTINGS,
            "👤My profile", Button.PROFILE,
            "📄ANALYTICS", Button.REPORT,
            "💳SUBSCRIPTION", Button.SUBSCRIPTION,
            "💎Invite", Button.INVITE
    );

    private final CalorieTexts texts;
    /** Подпись на любом языке (и старый текст команды) → команда. */
    private final Map<String, Command> commandsByLabel;

    public CalorieMenuButtons(CalorieTexts texts) {
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
        return buildKeyboard(texts.languageOf(appUserId));
    }

    /** Нижняя клавиатура бота на заданном языке — когда одну клавиатуру шлём многим (рассылка). */
    public ReplyKeyboardMarkup buildKeyboard(AppLanguage language) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        markup.setKeyboard(KEYBOARD_LAYOUT.stream().map(row -> {
            KeyboardRow keyboardRow = new KeyboardRow();
            row.forEach(button -> keyboardRow.add(getLabel(button, language)));
            return keyboardRow;
        }).toList());
        return markup;
    }

    /** То же меню кнопками под сообщением: подпись на языке пользователя, в callbackData — команда. Внизу «Закрыть». */
    public InlineKeyboardMarkup buildInlineMenu(Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>(INLINE_MENU_LAYOUT.stream().map(row -> row.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(getLabel(button, language))
                        .callbackData(button.getCommand().getCommandText())
                        .build())
                .toList()).toList());
        rows.add(List.of(TelegramKeyboard.button(
                texts.get(language, BotTextKeys.COMMON_BUTTON_CLOSE), Command.CALORIE_CLOSE.getCommandText())));
        return new InlineKeyboardMarkup(rows);
    }

    /** Последний ряд экрана раздела: «← Меню». */
    public InlineKeyboardButton buildBackToMenuButton(Long appUserId) {
        return buildBackToMenuButton(texts.languageOf(appUserId));
    }

    public InlineKeyboardButton buildBackToMenuButton(AppLanguage language) {
        return TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_TO_MENU), BACK_TO_MENU_DATA);
    }

    /** Клавиатура из одной «← Меню» — для экрана без своих кнопок (например, «список пуст»). */
    public InlineKeyboardMarkup buildBackToMenuKeyboard(Long appUserId) {
        return TelegramKeyboard.builder().row(buildBackToMenuButton(appUserId)).build();
    }

    /**
     * Кнопку нажали в сообщении с меню: раздел показываем на его месте, а не новым сообщением.
     * Telegram присылает сообщение вместе с текущей клавиатурой — на ней все кнопки меню.
     */
    public boolean isMenuKeyboard(MaybeInaccessibleMessage message) {
        if (!(message instanceof Message withContent) || withContent.getReplyMarkup() == null) return false;
        Set<String> callbacks = withContent.getReplyMarkup().getKeyboard().stream()
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData)
                .filter(data -> data != null)
                .collect(Collectors.toSet());
        return callbacks.containsAll(INLINE_MENU_CALLBACKS);
    }

    private static Map<String, Command> buildCommandsByLabel(CalorieTexts texts) {
        Map<String, Command> map = new HashMap<>();
        for (Button button : Button.values()) {
            putUnique(map, button.getCommand().getCommandText(), button.getCommand());
            for (AppLanguage language : AppLanguage.values()) {
                putUnique(map, texts.get(language, button.getTextKey()).strip(), button.getCommand());
            }
        }
        LEGACY_LABELS.forEach((label, button) -> putUnique(map, label, button.getCommand()));
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
