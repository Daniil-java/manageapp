package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.TelegramCalorieBotFacade;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Collections;

/**
 * Кнопки под карточкой блюда на языке пользователя: вес и число порций, избранное, удаление.
 */
@Component
@RequiredArgsConstructor
public class DishKeyboards {
    private static final String PORTION_COUNT_CMD = "PC";
    // Заглушка для кнопок-подписей, которые ничего не делают
    private static final String NO_ACTION = TelegramCalorieBotFacade.NO_ACTION_CALLBACK;
    // Пустое место вместо «➖» при одной порции — чтобы ряд не сдвигался, когда «➖» появится
    private static final String PLACEHOLDER_TEXT = "·";

    private final CalorieTexts texts;

    /** Кнопки под карточкой блюда: порции, «Сохранить блюдо», «Удалить из дневника». */
    public InlineKeyboardMarkup buildPortionKeyboard(Dish dish, Long appUserId) {
        return buildPortionKeyboard(dish, appUserId, false);
    }

    /** То же, но блюдо уже в избранном: вместо «Сохранить блюдо» — неактивная «✅ В избранном». */
    public InlineKeyboardMarkup buildFavoritePortionKeyboard(Dish dish, Long appUserId) {
        return buildPortionKeyboard(dish, appUserId, true);
    }

    /** Блюдо удалено из дневника: вместо кнопок одна неактивная «НЕ УЧИТЫВАЕТСЯ». */
    public InlineKeyboardMarkup buildNotCountedKeyboard(Long appUserId) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(texts.get(appUserId, BotTextKeys.DISH_BUTTON_NOT_COUNTED));
        button.setCallbackData(NO_ACTION);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(button)));
        return markup;
    }

    /**
     * [ −10% ] [ 150 г ] [ +10% ]
     * [  ➖  ] [ 2 порции ] [ ➕ ]
     * [ 🗑 Удалить ] [ ⭐ В избранное ]
     * Текущее значение — в средней кнопке, рядом с кнопками, которые его меняют.
     * «Удалить» слева: под большим пальцем правой руки чаще оказывается правая кнопка.
     */
    private InlineKeyboardMarkup buildPortionKeyboard(Dish dish, Long appUserId, boolean inFavorites) {
        AppLanguage language = texts.languageOf(appUserId);
        // Базовая часть для всех кнопок изменения порции
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                + TelegramBot.DEFAULT_DELIMETER;
        int portions = dish.getPortions();

        //<scale_command>delim<dishId>delim<+-scale>
        InlineKeyboardButton portionMinus = portions > 1
                ? TelegramKeyboard.button("➖", base + PORTION_COUNT_CMD + TelegramBot.DEFAULT_DELIMETER + (portions - 1))
                : TelegramKeyboard.button(PLACEHOLDER_TEXT, NO_ACTION);

        InlineKeyboardButton favorite = inFavorites
                ? TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_IN_FAVORITES), NO_ACTION)
                : TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_SAVE_FAVORITE),
                        Command.CALORIE_FAVORITE_ADD.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId());

        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("−10%", base + "-10"),
                        TelegramKeyboard.button(getWeightLabel(dish, language), NO_ACTION),
                        TelegramKeyboard.button("+10%", base + "10")
                )
                .row(
                        portionMinus,
                        TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_PORTIONS_VALUE, portions), NO_ACTION),
                        TelegramKeyboard.button("➕", base + PORTION_COUNT_CMD + TelegramBot.DEFAULT_DELIMETER + (portions + 1))
                )
                .row(
                        TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_DELETE),
                                Command.CALORIE_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId()),
                        favorite
                )
                .build();
    }

    /** «150 г» — вес одной порции; неизвестен — прочерк. */
    private String getWeightLabel(Dish dish, AppLanguage language) {
        return dish.getPortionWeight() == null
                ? "—"
                : texts.get(language, BotTextKeys.DISH_BUTTON_WEIGHT_VALUE, dish.getPortionWeight());
    }

    // Прежняя раскладка (до et-105): кнопки-подписи «Вес порции:» / «Порций:», «Сохранить» и «Удалить» отдельными рядами.
    // Ключи dish.button.portion-weight / dish.button.portions из словарей убраны — для возврата их нужно вернуть.
    // private InlineKeyboardMarkup buildPortionKeyboard(Dish dish, Long appUserId, boolean inFavorites) {
    //     AppLanguage language = texts.languageOf(appUserId);
    //     // Базовая часть для всех кнопок изменения порции
    //     String base = Command.CALORIE_SCALE.getCommandText()
    //             + TelegramBot.DEFAULT_DELIMETER + dish.getId()
    //             + TelegramBot.DEFAULT_DELIMETER;
    //
    //     //<scale_command>delim<dishId>delim<+-scale>
    //     TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
    //             .row(
    //                     TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_PORTION_WEIGHT), NO_ACTION),
    //                     TelegramKeyboard.button("-10%", base + "-10"),
    //                     TelegramKeyboard.button("+10%", base + "10")
    //             );
    //     String portionsLabel = texts.get(language, BotTextKeys.DISH_BUTTON_PORTIONS);
    //     if (dish.getPortions() > 1) {
    //         builder.row(
    //                 TelegramKeyboard.button(portionsLabel, NO_ACTION),
    //                 TelegramKeyboard.button(
    //                         "➖",
    //                         base + PORTION_COUNT_CMD
    //                                 + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() - 1)
    //                 ),
    //                 TelegramKeyboard.button(
    //                         "➕",
    //                         base + PORTION_COUNT_CMD
    //                                 + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() + 1)
    //                 )
    //         );
    //     } else {
    //         // Если порция одна — показываем только "+"
    //         builder.row(
    //                 TelegramKeyboard.button(portionsLabel, NO_ACTION),
    //                 TelegramKeyboard.button(
    //                         "➕",
    //                         base + PORTION_COUNT_CMD
    //                                 + TelegramBot.DEFAULT_DELIMETER + 2
    //                 )
    //         );
    //     }
    //
    //     if (inFavorites) {
    //         builder.row(TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_IN_FAVORITES), NO_ACTION));
    //     } else {
    //         builder.row(TelegramKeyboard.button(
    //                 texts.get(language, BotTextKeys.DISH_BUTTON_SAVE_FAVORITE),
    //                 Command.CALORIE_FAVORITE_ADD.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId()
    //         ));
    //     }
    //     return builder
    //             .row(TelegramKeyboard.button(
    //                     texts.get(language, BotTextKeys.DISH_BUTTON_DELETE),
    //                     Command.CALORIE_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId()
    //             ))
    //             .build();
    // }

}
