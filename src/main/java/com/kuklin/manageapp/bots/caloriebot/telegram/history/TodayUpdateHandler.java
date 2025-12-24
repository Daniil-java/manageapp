package com.kuklin.manageapp.bots.caloriebot.telegram.history;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TodayUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private final UserNutritionProfileService userNutritionProfileService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        sendTodayMessage(telegramUser.getTelegramId());
    }

    public void sendTodayMessage(Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        calorieTelegramBot.sendReturnedMessage(
                userId,
                getDishesString(dishes, profile),
                getStatsKeyboard(userId),
                null
        );
    }

    public static String getDishesString(List<Dish> dishes, UserNutritionProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 <b>Дневник питания (сегодня)</b>\n\n");

        int cal = 0, fats = 0, proteins = 0, carbs = 0;

        for (Dish dish : dishes) {
            sb.append(Dish.getInfo(dish)).append("\n");

            if (dish.getCalories() != null) cal += dish.getCalories();
            if (dish.getFats() != null) fats += dish.getFats();
            if (dish.getProteins() != null) proteins += dish.getProteins();
            if (dish.getCarbohydrates() != null) carbs += dish.getCarbohydrates();
        }

        sb.append("\n⚡️ <b>ИТОГО:</b>\n");

        appendTotal(sb, "🔥 К", cal, profile.getCaloriesNormPerDay(), "ккал");
        appendTotal(sb, "🥩 Б", proteins, profile.getProteinsNormGramsPerDay(), "г");
        appendTotal(sb, "🥑 Ж", fats, profile.getFatsNormGramsPerDay(), "г");
        appendTotal(sb, "🍞 У", carbs, profile.getCarbsNormGramsPerDay(), "г");

        return sb.toString();
    }

    private static void appendTotal(
            StringBuilder sb,
            String label,
            int total,
            Integer norm,
            String unit
    ) {
        sb.append(label)
                .append(": <b>")
                .append(total)
                .append("</b>");

        if (norm != null) {
            sb.append(" / ")
                    .append(norm)
                    .append(" ")
                    .append(unit);
        }

        sb.append("\n");
    }

    public InlineKeyboardMarkup getStatsKeyboard(Long userId) {
        String calories = analyticsService.getCaloriesBarButtonOrEmpty(userId);
        String proteins = analyticsService.getProteinsBarButtonOrEmpty(userId);
        String fats = analyticsService.getFatsBarButtonOrEmpty(userId);
        String carb = analyticsService.getCarbsBarButton(userId);
        String water = analyticsService.getWaterBarButton(userId);

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        if (!calories.isBlank()) {
            builder.row(TelegramKeyboard.button(calories, "temp"));
        }
        if (!proteins.isBlank()) {
            builder.row(TelegramKeyboard.button(proteins, "temp"));
        }
        if (!fats.isBlank()) {
            builder.row(TelegramKeyboard.button(fats, "temp"));
        }
        if (!carb.isBlank()) {
            builder.row(TelegramKeyboard.button(carb, "temp"));
        }
        if (!water.isBlank()) {
            builder.row(TelegramKeyboard.button(water, "temp"));
        }
        builder.row(TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText()));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TODAY_LIST.getCommandText();
    }
}
