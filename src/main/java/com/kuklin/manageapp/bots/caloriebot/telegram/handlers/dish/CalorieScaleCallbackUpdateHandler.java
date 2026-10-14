package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.texts.DishTexts;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
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
    private final DishTexts dishTexts;
    private final DishKeyboards dishKeyboards;
    private final CalorieTexts texts;
    //<command>delim<dishId>delim<+-scale%> - для увеличения веса порции
    private static final int WEIGHT_PORTIONS_PARTS = 3;
    //<command>delim<dishId>delim<cmd><value> - для увеличения количества порций
    private static final int COUNT_PORTIONS_PARTS = 4;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        String data = update.getCallbackQuery().getData();
        String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);

        Long appUserId = telegramUser.getAppUserId();
        Dish dish = null;
        try {
            if (parts.length == WEIGHT_PORTIONS_PARTS) {
                dish = handleWeightPortions(appUserId, parts);
            } else if (parts.length == COUNT_PORTIONS_PARTS) {
                dish = handleCountPortions(appUserId, parts);
            }
        } catch (Exception e) {
            log.error("Callback data error! {}", BotIdentifier.CALORIE_BOT);
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    texts.get(appUserId, BotTextKeys.DISH_SCALE_ERROR)
            );
            return;
        }
        // Блюдо удалено, чужое или значение вне допустимого — карточку не перерисовываем
        if (dish == null) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    texts.get(appUserId, BotTextKeys.DISH_SCALE_ERROR)
            );
            return;
        }
        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                dishTexts.buildDishCardText(dish, appUserId),
                update.getCallbackQuery().getMessage().getMessageId(),
                dishKeyboards.buildPortionKeyboard(dish, appUserId)
        );

    }

    //<command>delim<dishId>delim<+-scale%> - для увеличения веса порции
    private Dish handleWeightPortions(Long appUserId, String[] parts) {
        Long dishId = Long.parseLong(parts[1]);
        int scale = Integer.parseInt(parts[2]);

        return dishService.changePortionWeightByPercentOrNull(appUserId, dishId, scale);
    }

    //<command>delim<dishId>delim<cmd><value> - для увеличения количества порций
    private Dish handleCountPortions(Long appUserId, String[] parts) {
        Long dishId = Long.parseLong(parts[1]);
        Integer value = Integer.parseInt(parts[3]);

        return dishService.saveNewPortionsCountOrNull(appUserId, dishId, value);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SCALE.getCommandText();
    }
}
