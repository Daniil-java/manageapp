package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListWeekUpdateHandler implements CalorieBotUpdateHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getWeekDishes(telegramUser.getTelegramId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                getDishesString(dishes),
                StartUpdateHandler.getCommandKeyboard(),
                null
        );
    }

    private String getDishesString(List<Dish> dishes) {
        StringBuilder sb = new StringBuilder();

        // группируем по дате
        Map<LocalDate, List<Dish>> byDay = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated().toLocalDate(),
                        TreeMap::new, Collectors.toList()));

        int totalCalories = 0, totalProteins = 0, totalFats = 0, totalCarbs = 0;

        for (Map.Entry<LocalDate, List<Dish>> entry : byDay.entrySet()) {
            LocalDate day = entry.getKey();
            List<Dish> dayDishes = entry.getValue();

            sb.append("📅 <b>").append(day).append("</b>\n");

            int dayCalories = 0, dayProteins = 0, dayFats = 0, dayCarbs = 0;

            for (Dish dish : dayDishes) {
                sb.append("🍽 <b>").append(dish.getName()).append("</b> ")
                        .append("🔥 ").append(dish.getCalories()).append("ккал ")
                        .append("💪 ").append(dish.getProteins()).append("Б ")
                        .append("🥑 ").append(dish.getFats()).append("Ж ")
                        .append("🌾 ").append(dish.getCarbohydrates()).append("У\n");

                dayCalories += dish.getCalories();
                dayProteins += dish.getProteins();
                dayFats += dish.getFats();
                dayCarbs += dish.getCarbohydrates();
            }

            sb.append("<b>— Итого за день: </b>")
                    .append("🔥 ").append(dayCalories).append(" ккал ")
                    .append("💪 ").append(dayProteins).append(" Б ")
                    .append("🥑 ").append(dayFats).append(" Ж ")
                    .append("🌾 ").append(dayCarbs).append(" У\n\n");

            totalCalories += dayCalories;
            totalProteins += dayProteins;
            totalFats += dayFats;
            totalCarbs += dayCarbs;
        }

        sb.append("📊 <b>Итого за 7 дней:</b>\n")
                .append("🔥 ").append(totalCalories).append("ккал ")
                .append("💪 ").append(totalProteins).append("Б ")
                .append("🥑 ").append(totalFats).append("Ж ")
                .append("🌾 ").append(totalCarbs).append("У");

        return sb.toString().trim();

    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEEK_LIST.getCommandText();
    }
}
