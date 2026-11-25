package com.kuklin.manageapp.bots.caloriebot.dish.telegram;

import com.kuklin.manageapp.bots.caloriebot.dish.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

/**
 * Кнопки под карточкой блюда на языке пользователя: вес и число порций, избранное, удаление.
 */
@Component
@RequiredArgsConstructor
public class DishKeyboards {
    private static final String PORTION_COUNT_CMD = "PC";

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
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.noActionButton(texts.get(appUserId, BotTextKeys.DISH_BUTTON_NOT_COUNTED)))
                .build();
    }

    /**
     * [ −10% ] [ 150 г ] [ +10% ]
     * [  ➖  ] [ 2 порции ] [ ➕ ]
     * [ 🗑 Удалить ] [ ⭐ В избранное ]
     * Текущее значение — в средней кнопке, рядом с кнопками, которые его меняют.
     * «Удалить» слева: под большим пальцем правой руки чаще оказывается правая кнопка.
     */
    private InlineKeyboardMarkup buildPortionKeyboard(Dish dish, Long appUserId, boolean inFavorites) {
        // Базовая часть для всех кнопок изменения порции: <scale_command>delim<dishId>delim<+-scale>
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                + TelegramBot.DEFAULT_DELIMETER;
        int portions = dish.getPortions();
        String portionsBase = base + PORTION_COUNT_CMD + TelegramBot.DEFAULT_DELIMETER;
        PortionCallbacks callbacks = new PortionCallbacks(
                base + "-10",
                base + "10",
                portionsBase + (portions - 1),
                portionsBase + (portions + 1),
                Command.CALORIE_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId(),
                inFavorites ? null
                        : Command.CALORIE_FAVORITE_ADD.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId());
        return buildPortionKeyboard(dish, texts.languageOf(appUserId), callbacks);
    }

    /**
     * callbackData кнопок под карточкой. favorite == null — блюдо уже в избранном (неактивная «✅ В избранном»).
     */
    public record PortionCallbacks(String weightMinus, String weightPlus, String portionsMinus, String portionsPlus,
                                   String delete, String favorite) {
    }

    /**
     * Та же раскладка с любыми callbackData — для карточки-примера новичку (SampleDishCalorieUpdateHandler):
     * кнопки выглядят как настоящие, а обрабатывает их пример.
     */
    public InlineKeyboardMarkup buildPortionKeyboard(Dish dish, AppLanguage language, PortionCallbacks callbacks) {
        int portions = dish.getPortions();
        InlineKeyboardButton portionMinus = portions > 1
                ? TelegramKeyboard.button("➖", callbacks.portionsMinus())
                // Пустое место вместо «➖» при одной порции — ряд не сдвинется, когда «➖» появится
                : TelegramKeyboard.placeholderButton();

        InlineKeyboardButton favorite = callbacks.favorite() == null
                ? TelegramKeyboard.noActionButton(texts.get(language, BotTextKeys.DISH_BUTTON_IN_FAVORITES))
                : TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_SAVE_FAVORITE), callbacks.favorite());

        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("−10%", callbacks.weightMinus()),
                        TelegramKeyboard.noActionButton(getWeightLabel(dish, language)),
                        TelegramKeyboard.button("+10%", callbacks.weightPlus())
                )
                .row(
                        portionMinus,
                        TelegramKeyboard.noActionButton(texts.get(language, BotTextKeys.DISH_BUTTON_PORTIONS_VALUE, portions)),
                        TelegramKeyboard.button("➕", callbacks.portionsPlus())
                )
                .row(
                        TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_DELETE), callbacks.delete(),
                                ButtonStyle.DANGER),
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
