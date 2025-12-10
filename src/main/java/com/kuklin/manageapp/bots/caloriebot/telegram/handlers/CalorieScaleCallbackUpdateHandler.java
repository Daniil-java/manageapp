package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieScaleCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private static final String ERROR_MSG = "Не получилось выполнить операцию!";
    //<command>delim<dishId>delim<+-scale>
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        String data = query.getData();
        Long chatId = query.getMessage().getChatId();

        Long dishId = extractDishId(data);
        Integer scale = extractScale(data);

        if (dishId == null || scale == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
        }
        Dish dish = dishService.changeDishByPercent(dishId, scale);

        calorieTelegramBot.sendEditMessage(
                chatId,
                Dish.getInfo(dish),
                query.getMessage().getMessageId(),
                DishUpdateHandler.getPortionKeyboard(dishId)
                );
    }

    //<command>delim<dishId>delim<+-scale
    private Integer extractScale(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            Integer scale = Integer.parseInt(parts[2]);
            if (scale > 100 || -100 > scale ) return null;
            return scale;
        } catch (Exception e) {
            return null;
        }
    }

    //<command>delim<dishId>delim<+-scale
    private Long extractDishId(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SCALE.getCommandText();
    }
}
