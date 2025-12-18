package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
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
public class TodayUpdateHandler implements CalorieBotUpdateHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private final UserNutritionProfileService userNutritionProfileService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getTodayDishes(telegramUser.getTelegramId());
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getTelegramId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                getDishesString(dishes, profile),
                getStatsKeyboard(telegramUser.getTelegramId()),
                null
        );
    }

    public static String getDishesString(List<Dish> dishes, UserNutritionProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 <b>Дневник питания (сегодня)</b>\n\n");

        int cal = 0, fats = 0, proteins = 0, carbHyd = 0;
        for (Dish dish : dishes) {
            sb.append(Dish.getInfo(dish)).append("\n");
            cal += dish.getCalories() != null ? dish.getCalories() : 0;
            fats += dish.getFats() != null ? dish.getFats() : 0;
            proteins += dish.getProteins() != null ? dish.getProteins() : 0;
            carbHyd += dish.getCarbohydrates() != null ? dish.getCarbohydrates() : 0;
        }

        // Формируем вертикальный блок итогов
        sb.append("\n⚡️ <b>ИТОГО:</b>\n")
                .append("🔥 К: <b>").append(cal).append("</b> / ").append(profile.getCaloriesNormPerDay()).append(" ккал\n")
                .append("🥩 Б: <b>").append(proteins).append("</b> / ").append(profile.getProteinsNormGramsPerDay()).append(" г\n")
                .append("🥑 Ж: <b>").append(fats).append("</b> / ").append(profile.getFatsNormGramsPerDay()).append(" г\n")
                .append("🍞 У: <b>").append(carbHyd).append("</b> / ").append(profile.getCarbsNormGramsPerDay());

        return sb.toString();
    }

    public InlineKeyboardMarkup getStatsKeyboard(Long userId) {
        String calories = analyticsService.getCaloriesBarButton(userId);
        String proteins = analyticsService.getProteinsBarButton(userId);
        String fats = analyticsService.getFatsBarButton(userId);
        String carb = analyticsService.getCarbsBarButton(userId);
        String water = analyticsService.getWaterBarButton(userId);

        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button(calories, "temp")
                ).row(
                        TelegramKeyboard.button(proteins, "temp")
                ).row(
                        TelegramKeyboard.button(fats, "temp")
                ).row(
                        TelegramKeyboard.button(carb, "temp")
                ).row(
                        TelegramKeyboard.button(water, "temp")
                ).row(
                        TelegramKeyboard.button("Закрыть", Command.CALORIE_CLOSE.getCommandText())
                )
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TODAY_LIST.getCommandText();
    }
}
