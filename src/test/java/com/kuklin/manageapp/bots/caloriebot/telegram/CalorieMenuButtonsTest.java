package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons.Button;
import com.kuklin.manageapp.common.library.tgutils.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.InaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalorieMenuButtonsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;

    private CalorieMenuButtons buttons;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        buttons = new CalorieMenuButtons(new CalorieTexts(new I18nProperties(), settings));
    }

    /** Подписи до смены стиля — из клавиатур, которые уже висят в чатах: русские = тексты команд, английские — алиасы. */
    @Test
    void oldLabelsStillResolve() {
        for (Button button : Button.values()) {
            assertThat(buttons.findCommand(button.getCommand().getCommandText())).as(button.name())
                    .contains(button.getCommand());
        }
        assertThat(buttons.findCommand("📊TODAY")).contains(Command.CALORIE_TODAY_LIST);
        assertThat(buttons.findCommand("🌟 FAVORITE DISHES")).contains(Command.CALORIE_FAVORITE);
        assertThat(buttons.findCommand("⚙Settings")).contains(Command.CALORIE_SETTINGS);
        assertThat(buttons.findCommand("👤My profile")).contains(Command.CALORIE_PROFILE);
        assertThat(buttons.findCommand("📄ANALYTICS")).contains(Command.CALORIE_REPORT);
        assertThat(buttons.findCommand("💳SUBSCRIPTION")).contains(Command.CALORIE_PAYMENT_PAYLOAD_PLAN);
        assertThat(buttons.findCommand("💎Invite")).contains(Command.CALORIE_UTM);
    }

    /** Тексты команд не меняются: они префиксы в callbackData, по ним фасад ищет обработчик. */
    @Test
    void labelsAreNotCommandTexts() {
        assertThat(buttons.getLabel(Button.SETTINGS, AppLanguage.RU)).isEqualTo("⚙️ Настройки");
        assertThat(Command.CALORIE_SETTINGS.getCommandText()).isEqualTo("⚙Настройки");
    }

    @Test
    void everyLabelInEveryLanguageResolvesToItsCommand() {
        for (Button button : Button.values()) {
            for (AppLanguage language : AppLanguage.values()) {
                assertThat(buttons.findCommand(buttons.getLabel(button, language)))
                        .as("%s in %s", button, language)
                        .contains(button.getCommand());
            }
        }
    }

    @Test
    void englishLabelIsTranslated() {
        assertThat(buttons.getLabel(Button.SETTINGS, AppLanguage.EN)).isEqualTo("⚙️ Settings");
        assertThat(buttons.findCommand("⚙️ Settings")).contains(Command.CALORIE_SETTINGS);
        assertThat(buttons.findCommand("  ⚙️ Settings ")).contains(Command.CALORIE_SETTINGS);
    }

    @Test
    void otherTextIsNotAButton() {
        assertThat(buttons.findCommand("гречка 200 г")).isEmpty();
        assertThat(buttons.findCommand("⚙Settings please")).isEmpty();
        assertThat(buttons.findCommand(null)).isEmpty();
    }

    @Test
    void keyboardIsInUserLanguage() {
        assertThat(labels(buttons.buildKeyboard(EN_USER))).containsExactly(
                List.of("📊 Today"),
                List.of("💧 Water", "⭐ Favorites"),
                List.of("📄 Analytics", "☰ Menu"));
        assertThat(labels(buttons.buildKeyboard(RU_USER))).containsExactly(
                List.of("📊 Сегодня"),
                List.of("💧 Вода", "⭐ Избранное"),
                List.of("📄 Аналитика", "☰ Меню"));
    }

    /** Подсказка в пустом поле ввода — на языке пользователя, в лимит Telegram (64 символа). */
    @Test
    void keyboardHasInputPlaceholder() {
        String ru = buttons.buildKeyboard(RU_USER).getInputFieldPlaceholder();
        String en = buttons.buildKeyboard(EN_USER).getInputFieldPlaceholder();

        assertThat(ru).isEqualTo("Напиши, что съел, или пришли фото 🍽");
        assertThat(en).isEqualTo("Type what you ate or send a photo 🍽");
        assertThat(List.of(ru, en)).allMatch(text -> text.codePointCount(0, text.length()) <= 64);
    }

    @Test
    void menuButtonOpensMenu() {
        assertThat(buttons.findCommand("☰ Меню")).contains(Command.CALORIE_MENU);
        assertThat(buttons.findCommand("☰ Menu")).contains(Command.CALORIE_MENU);
    }

    /** Кнопки, убранные с нижней клавиатуры, нажимаются из старых клавиатур в чатах. */
    @Test
    void removedKeyboardButtonsStillResolve() {
        assertThat(buttons.findCommand("⚙️ Настройки")).contains(Command.CALORIE_SETTINGS);
        assertThat(buttons.findCommand("💎 Пригласить")).contains(Command.CALORIE_UTM);
    }

    /** В /menu — все разделы без кнопки самого меню, внизу «Закрыть». */
    @Test
    void inlineMenuHasAllSectionsAndClose() {
        List<List<InlineKeyboardButton>> rows = buttons.buildInlineMenu(EN_USER).getKeyboard();
        List<String> texts = rows.stream().flatMap(List::stream).map(InlineKeyboardButton::getText).toList();

        assertThat(texts).hasSize(Button.values().length).doesNotContain("☰ Menu");
        assertThat(rows.get(rows.size() - 1).get(0).getCallbackData()).isEqualTo(Command.CALORIE_CLOSE.getCommandText());
    }

    /** «← Меню» уходит в MenuCalorieUpdateHandler: фасад берёт первое слово callbackData. */
    @Test
    void backToMenuButtonOpensMenu() {
        InlineKeyboardButton back = buttons.buildBackToMenuButton(RU_USER);

        assertThat(back.getText()).isEqualTo("← Меню");
        assertThat(back.getCallbackData()).isEqualTo(CalorieMenuButtons.BACK_TO_MENU_DATA);
        assertThat(back.getCallbackData().split(" ")[0]).isEqualTo(Command.CALORIE_MENU.getCommandText());
    }

    @Test
    void menuKeyboardIsRecognized() {
        assertThat(buttons.isMenuKeyboard(buildMessage(buttons.buildInlineMenu(RU_USER)))).isTrue();
        assertThat(buttons.isMenuKeyboard(buildMessage(buttons.buildInlineMenu(EN_USER)))).isTrue();
    }

    /** Экран раздела, сообщение без кнопок и недоступное сообщение — не меню: раздел приходит новым сообщением. */
    @Test
    void otherKeyboardsAreNotMenu() {
        assertThat(buttons.isMenuKeyboard(buildMessage(buttons.buildBackToMenuKeyboard(RU_USER)))).isFalse();
        assertThat(buttons.isMenuKeyboard(buildMessage(null))).isFalse();
        assertThat(buttons.isMenuKeyboard(new InaccessibleMessage())).isFalse();
        assertThat(buttons.isMenuKeyboard(null)).isFalse();
    }

    private static Message buildMessage(InlineKeyboardMarkup markup) {
        Message message = new Message();
        message.setReplyMarkup(markup);
        return message;
    }

    /** В кнопках под сообщением подпись переведена, а callbackData — прежний текст команды. */
    @Test
    void inlineMenuKeepsCommandInCallbackData() {
        InlineKeyboardMarkup menu = buttons.buildInlineMenu(EN_USER);
        InlineKeyboardButton settings = menu.getKeyboard().get(2).get(0);

        assertThat(settings.getText()).isEqualTo("⚙️ Settings");
        assertThat(settings.getCallbackData()).isEqualTo(Command.CALORIE_SETTINGS.getCommandText());
    }

    private static List<List<String>> labels(ReplyKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .map(row -> row.stream().map(KeyboardButton::getText).toList())
                .toList();
    }
}
