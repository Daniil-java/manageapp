package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.texts.WeekTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListWeekUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieMenuButtons menuButtons;
    private final WeekTexts weekTexts;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getWeekDishes(telegramUser.getAppUserId());
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                weekTexts.buildWeekReportText(dishes, profile, telegramUser.getAppUserId()),
                menuButtons.buildKeyboard(telegramUser.getAppUserId()),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEEK_LIST.getCommandText();
    }
}
