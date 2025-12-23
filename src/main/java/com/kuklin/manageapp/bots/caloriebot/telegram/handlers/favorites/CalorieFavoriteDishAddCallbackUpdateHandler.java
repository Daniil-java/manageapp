package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.favorites;

import com.kuklin.manageapp.bots.caloriebot.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.DishUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class CalorieFavoriteDishAddCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserFavoriteDishService userFavoriteDishService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();

        Long dishId = extractDishIdOrNull(query.getData());
        var favoriteDish = userFavoriteDishService
                .saveFromDish(telegramUser.getTelegramId(), dishId);

        if (favoriteDish == null) {
            calorieTelegramBot.sendReturnedMessage(
                    query.getMessage().getChatId(),
                    "Не получилось сохранить блюдо! Возможно у вас уже сохранено блюдо с таким названием!"
            );
            return;
        }

        calorieTelegramBot.sendReturnedMessage(
                query.getMessage().getChatId(),
                "⭐ Добавил блюдо в список сохранённых."
        );

        calorieTelegramBot.sendEditMessage(
                query.getMessage().getChatId(),
                query.getMessage().getText(),                         // текст оставляем как был
                query.getMessage().getMessageId(),
                DishUpdateHandler.getPortionKeyboardFavorite(dishId)  // новая клавиатура
        );
    }

    private Long extractDishIdOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_FAVORITE_ADD.getCommandText();
    }
}
