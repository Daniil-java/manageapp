package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.i18n.I18nProperties;
import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.common.library.tgutils.StyledInlineKeyboardButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DishKeyboardsTest {

    private static final Long RU_USER = 1L;
    private static final Long EN_USER = 2L;
    private static final String NO_ACTION = TelegramCalorieBotFacade.NO_ACTION_CALLBACK;

    private DishKeyboards keyboards;

    @BeforeEach
    void setUp() {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.findLanguageCode(RU_USER)).thenReturn("ru");
        when(settings.findLanguageCode(EN_USER)).thenReturn("en");
        keyboards = new DishKeyboards(new CalorieTexts(new I18nProperties(), settings));
    }

    @Test
    void valuesInMiddleButtons() {
        List<List<String>> labels = getLabels(keyboards.buildPortionKeyboard(buildDish(150, 2), RU_USER));

        assertThat(labels).containsExactly(
                List.of("−10%", "150 г", "+10%"),
                List.of("➖", "2 порции", "➕"),
                List.of("🗑 Удалить", "⭐ В избранное"));
    }

    /** Одна порция: вместо «➖» — заглушка, ряд не сдвигается, когда «➖» появится. */
    @Test
    void singlePortionKeepsLayout() {
        InlineKeyboardMarkup markup = keyboards.buildPortionKeyboard(buildDish(150, 1), RU_USER);
        List<InlineKeyboardButton> portionsRow = markup.getKeyboard().get(1);

        assertThat(portionsRow).hasSize(3);
        assertThat(portionsRow.get(0).getText()).isEqualTo("·");
        assertThat(portionsRow.get(0).getCallbackData()).isEqualTo(NO_ACTION);
        assertThat(portionsRow.get(1).getText()).isEqualTo("1 порция");
    }

    /** Кнопки меняют порцию прежними callbackData — старые карточки в чатах работают. */
    @Test
    void callbacksAreUnchanged() {
        InlineKeyboardMarkup markup = keyboards.buildPortionKeyboard(buildDish(150, 2), RU_USER);

        assertThat(markup.getKeyboard().get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("/scale 42 -10", NO_ACTION, "/scale 42 10");
        assertThat(markup.getKeyboard().get(1)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("/scale 42 PC 1", NO_ACTION, "/scale 42 PC 3");
        assertThat(markup.getKeyboard().get(2)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("/deleteDish 42", "/favoriteadd 42");
    }

    /** Красная только «Удалить», остальные кнопки карточки — без цвета. */
    @Test
    void onlyDeleteIsRed() {
        InlineKeyboardMarkup markup = keyboards.buildPortionKeyboard(buildDish(150, 2), RU_USER);

        assertThat(markup.getKeyboard()).flatExtracting(row -> row.stream().map(DishKeyboardsTest::getStyle).toList())
                .containsExactly(null, null, null, null, null, null, "danger", null);
    }

    @Test
    void inFavoritesReplacesSaveButton() {
        List<InlineKeyboardButton> lastRow = keyboards.buildFavoritePortionKeyboard(buildDish(150, 2), RU_USER)
                .getKeyboard().get(2);

        assertThat(lastRow.get(0).getText()).isEqualTo("🗑 Удалить");
        assertThat(lastRow.get(1).getText()).isEqualTo("✅ В избранном");
        assertThat(lastRow.get(1).getCallbackData()).isEqualTo(NO_ACTION);
    }

    @Test
    void englishPluralAndUnknownWeight() {
        List<List<String>> labels = getLabels(keyboards.buildPortionKeyboard(buildDish(null, 5), EN_USER));

        assertThat(labels.get(0).get(1)).isEqualTo("—");
        assertThat(labels.get(1).get(1)).isEqualTo("5 portions");
        assertThat(labels.get(2)).containsExactly("🗑 Delete", "⭐ To favorites");
    }

    @Test
    void russianPluralForms() {
        assertThat(getPortionsLabel(5)).isEqualTo("5 порций");
        assertThat(getPortionsLabel(21)).isEqualTo("21 порция");
        assertThat(getPortionsLabel(3)).isEqualTo("3 порции");
    }

    private String getPortionsLabel(int portions) {
        return keyboards.buildPortionKeyboard(buildDish(100, portions), RU_USER).getKeyboard().get(1).get(1).getText();
    }

    private static Dish buildDish(Integer portionWeight, int portions) {
        Dish dish = new Dish();
        dish.setId(42L);
        dish.setPortionWeight(portionWeight);
        dish.setPortions(portions);
        return dish;
    }

    private static List<List<String>> getLabels(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .map(row -> row.stream().map(InlineKeyboardButton::getText).toList())
                .toList();
    }
    private static String getStyle(InlineKeyboardButton button) {
        return button instanceof StyledInlineKeyboardButton styled ? styled.getStyle() : null;
    }
}
