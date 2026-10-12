package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
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
    private static final String NO_ACTION = "temp";

    private final CalorieTextsService texts;

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

    private InlineKeyboardMarkup buildPortionKeyboard(Dish dish, Long appUserId, boolean inFavorites) {
        AppLanguage language = texts.languageOf(appUserId);
        // Базовая часть для всех кнопок изменения порции
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                + TelegramBot.DEFAULT_DELIMETER;

        //<scale_command>delim<dishId>delim<+-scale>
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_PORTION_WEIGHT), NO_ACTION),
                        TelegramKeyboard.button("-10%", base + "-10"),
                        TelegramKeyboard.button("+10%", base + "10")
                );
        String portionsLabel = texts.get(language, BotTextKeys.DISH_BUTTON_PORTIONS);
        if (dish.getPortions() > 1) {
            builder.row(
                    TelegramKeyboard.button(portionsLabel, NO_ACTION),
                    TelegramKeyboard.button(
                            "➖",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() - 1)
                    ),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() + 1)
                    )
            );
        } else {
            // Если порция одна — показываем только "+"
            builder.row(
                    TelegramKeyboard.button(portionsLabel, NO_ACTION),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + 2
                    )
            );
        }

        if (inFavorites) {
            builder.row(TelegramKeyboard.button(texts.get(language, BotTextKeys.DISH_BUTTON_IN_FAVORITES), NO_ACTION));
        } else {
            builder.row(TelegramKeyboard.button(
                    texts.get(language, BotTextKeys.DISH_BUTTON_SAVE_FAVORITE),
                    Command.CALORIE_FAVORITE_ADD.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId()
            ));
        }
        return builder
                .row(TelegramKeyboard.button(
                        texts.get(language, BotTextKeys.DISH_BUTTON_DELETE),
                        Command.CALORIE_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                ))
                .build();
    }
}
