package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieScaleCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private static final String ERROR_MSG = "Не получилось выполнить операцию!";
    //<command>delim<dishId>delim<+-scale%> - для увеличения веса порции
    private static final int WEIGHT_PORTIONS_PARTS = 3;
    //<command>delim<dishId>delim<cmd><value> - для увеличения количества порций
    private static final int COUNT_PORTIONS_PARTS = 4;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        String data = update.getCallbackQuery().getData();
        String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);

        Dish dish = null;
        try {
            if (parts.length == WEIGHT_PORTIONS_PARTS) {
                dish = handleWeightPortions(parts);
            } else if (parts.length == COUNT_PORTIONS_PARTS) {
                dish = handleCountPortions(parts);
            }
        } catch (Exception e) {
            log.error("Callback data error! {}", BotIdentifier.CALORIE_BOT);
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    ERROR_MSG
            );
            return;
        }
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                analyticsService.getInfo(dish, telegramUser.getTelegramId()),
                update.getCallbackQuery().getMessage().getMessageId(),
                DishUpdateHandler.getPortionWeightKeyboard(dish)
        );

    }

    //<command>delim<dishId>delim<+-scale%> - для увеличения веса порции
    private Dish handleWeightPortions(String[] parts) {
        Long dishId = Long.parseLong(parts[1]);
        Integer scale = Integer.parseInt(parts[2]);

        return dishService.changePortionWeightByPercent(dishId, scale);
    }

    //<command>delim<dishId>delim<cmd><value> - для увеличения количества порций
    private Dish handleCountPortions(String[] parts) {
        Long dishId = Long.parseLong(parts[1]);
        Integer value = Integer.parseInt(parts[3]);

        return dishService.saveNewPortionsCountOrNull(dishId, value);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SCALE.getCommandText();
    }
}
