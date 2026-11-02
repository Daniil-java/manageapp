package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.StyledInlineKeyboardButton;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeyboardTemplatesTest {

    private static final String NO_ACTION = TelegramCalorieBotFacade.NO_ACTION_CALLBACK;
    private static final String AGE_BASE = "/profileedit AGE ";

    @Test
    void numericLayout() {
        InlineKeyboardMarkup markup = buildAge(30, 1, 130);

        assertThat(getTexts(markup)).containsExactly(
                List.of("Возраст: 30"),
                List.of("−10", "−5", "−1"),
                List.of("+1", "+5", "+10"),
                List.of("← Назад", "✅ Сохранить"));
        assertThat(markup.getKeyboard().get(0).get(0).getCallbackData()).isEqualTo(NO_ACTION);
        assertThat(getCallbacks(markup.getKeyboard().get(2)))
                .containsExactly(AGE_BASE + "ADJ 31", AGE_BASE + "ADJ 35", AGE_BASE + "ADJ 40");
        assertThat(getCallbacks(markup.getKeyboard().get(3)))
                .containsExactly("/profileedit", AGE_BASE + "SET 30");
        // Зелёная только «Сохранить»
        assertThat(((StyledInlineKeyboardButton) markup.getKeyboard().get(3).get(1)).getStyle()).isEqualTo("success");
        assertThat(markup.getKeyboard().get(3).get(0)).isNotInstanceOf(StyledInlineKeyboardButton.class);
    }

    /** Шаг за границу ставит границу, кнопки на месте. */
    @Test
    void stepIsClampedToBound() {
        InlineKeyboardMarkup markup = buildAge(3, 1, 130);

        assertThat(getCallbacks(markup.getKeyboard().get(1)))
                .containsExactly(AGE_BASE + "ADJ 1", AGE_BASE + "ADJ 1", AGE_BASE + "ADJ 2");
    }

    /** На самой границе шаги в её сторону ничего не делают, ряд того же размера. */
    @Test
    void stepsAtBoundDoNothing() {
        InlineKeyboardMarkup markup = buildAge(130, 1, 130);

        assertThat(markup.getKeyboard().get(2)).hasSize(3);
        assertThat(getCallbacks(markup.getKeyboard().get(2))).containsOnly(NO_ACTION);
    }

    /** Норма без верхней границы (Integer.MAX_VALUE) — шаг вверх не переполняется. */
    @Test
    void noOverflowNearIntMax() {
        InlineKeyboardMarkup markup = KeyboardTemplates.buildNumericKeyboard(
                Command.CALORIE_PROFILE_EDIT, ProfileEditAction.CALORIE_NORM, Integer.MAX_VALUE - 10, 2000,
                0, Integer.MAX_VALUE, 50, 100, 500, v -> v, "Сохранить", "← Назад");

        assertThat(getCallbacks(markup.getKeyboard().get(2)))
                .containsOnly("/profileedit CALORIES ADJ " + Integer.MAX_VALUE);
    }

    @Test
    void decimalLabelsAndTargets() {
        InlineKeyboardMarkup markup = KeyboardTemplates.buildDecimalKeyboard(
                Command.CALORIE_PROFILE_EDIT, ProfileEditAction.CURRENT_WEIGHT, new BigDecimal("70.0"), new BigDecimal("70.0"),
                new BigDecimal("30.0"), new BigDecimal("300.0"),
                new BigDecimal("0.1"), new BigDecimal("1.0"), new BigDecimal("5.0"),
                v -> "Вес: " + v, "Сохранить", "← Назад");

        assertThat(getTexts(markup).get(1)).containsExactly("−5", "−1", "−0.1");
        assertThat(getTexts(markup).get(2)).containsExactly("+0.1", "+1", "+5");
        assertThat(getCallbacks(markup.getKeyboard().get(2))).containsExactly(
                "/profileedit CUR_WEIGHT ADJ 70.1", "/profileedit CUR_WEIGHT ADJ 71.0", "/profileedit CUR_WEIGHT ADJ 75.0");
    }

    /** В пошаговом заполнении «← Назад» ведёт на экран профиля, а не к списку полей редактора. */
    @Test
    void backFromDialogueGoesToProfile() {
        assertThat(KeyboardTemplates.getBackData(Command.CALORIE_PROFILE_EDIT)).isEqualTo("/profileedit");
        assertThat(KeyboardTemplates.getBackData(Command.CALORIE_PROFILE_DIALOGUE))
                .isEqualTo(Command.CALORIE_PROFILE.getCommandText());
    }

    @Test
    void enumMarksCurrentAndHasBack() {
        InlineKeyboardMarkup markup = KeyboardTemplates.buildEnumKeyboard(
                Command.CALORIE_PROFILE_EDIT, ProfileEditAction.SEX, EnumSet.allOf(UserNutritionProfile.Sex.class), 2,
                value -> ((UserNutritionProfile.Sex) value).name(), UserNutritionProfile.Sex.FEMALE, "← Назад");

        assertThat(getTexts(markup)).containsExactly(List.of("MALE", "✅ FEMALE"), List.of("← Назад"));
        assertThat(markup.getKeyboard().get(1).get(0).getCallbackData()).isEqualTo("/profileedit");
    }

    @Test
    void enumWithoutValueHasNoMark() {
        InlineKeyboardMarkup markup = KeyboardTemplates.buildEnumKeyboard(
                Command.CALORIE_PROFILE_EDIT, ProfileEditAction.SEX, EnumSet.allOf(UserNutritionProfile.Sex.class), 2,
                value -> ((UserNutritionProfile.Sex) value).name(), null, "← Назад");

        assertThat(getTexts(markup).get(0)).containsExactly("MALE", "FEMALE");
    }

    private static InlineKeyboardMarkup buildAge(int value, int min, int max) {
        return KeyboardTemplates.buildNumericKeyboard(Command.CALORIE_PROFILE_EDIT, ProfileEditAction.AGE, value, 20,
                min, max, 1, 5, 10, v -> "Возраст: " + v, "Сохранить", "← Назад");
    }

    private static List<List<String>> getTexts(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .map(row -> row.stream().map(InlineKeyboardButton::getText).toList())
                .toList();
    }

    private static List<String> getCallbacks(List<InlineKeyboardButton> row) {
        return row.stream().map(InlineKeyboardButton::getCallbackData).toList();
    }
}
