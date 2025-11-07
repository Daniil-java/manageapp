package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TodayUpdateHandler implements CalorieBotUpdateHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getTodayDishes(telegramUser.getTelegramId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                getDishesString(dishes),
                StartUpdateHandler.getCommandKeyboard(),
                null
        );
    }

    public static String getDishesString(List<Dish> dishes) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 <b>Дневник питания (сегодня)</b>\n\n");

        int cal = 0, fats = 0, proteins = 0, carbHyd = 0;
        for (Dish dish : dishes) {
            sb.append("🍽 ").append(Dish.getInfo(dish)).append("\n");
            cal += dish.getCalories() != null ? dish.getCalories() : 0;
            fats += dish.getFats() != null ? dish.getFats() : 0;
            proteins += dish.getProteins() != null ? dish.getProteins() : 0;
            carbHyd += dish.getCarbohydrates() != null ? dish.getCarbohydrates() : 0;
        }

        sb.append("\n")
                .append("⚡ <b>ИТОГО:</b> ")
                .append("🔥 Ккал: <b>").append(cal).append("</b> ")
                .append("🥩 Б: <b>").append(proteins).append(" г</b> ")
                .append("🥑 Ж: <b>").append(fats).append(" г</b> ")
                .append("🍞 У: <b>").append(carbHyd).append(" г</b>");

        return sb.toString();

    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TODAY_LIST.getCommandText();
    }
}
