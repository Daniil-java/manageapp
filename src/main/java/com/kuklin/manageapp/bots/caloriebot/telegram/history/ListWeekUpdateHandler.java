package com.kuklin.manageapp.bots.caloriebot.telegram.history;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.StartUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListWeekUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final UserNutritionProfileService userNutritionProfileService;
    private final UserSettingsService userSettingsService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getWeekDishes(telegramUser.getTelegramId());
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getTelegramId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                getWeeklyAnalytics(dishes, profile, telegramUser),
                StartUpdateHandler.getCommandKeyboard(),
                null
        );
    }

    private String getWeeklyAnalytics(List<Dish> dishes, UserNutritionProfile profile, TelegramUser telegramUser) {
        StringBuilder sb = new StringBuilder();

        // Группируем по дате
        ZoneId userZone = userSettingsService.getOrCreate(telegramUser.getTelegramId()).getZoneId();

        Map<LocalDate, List<Dish>> byDay = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated()
                                .atZone(userZone) // Переводим Instant в ZonedDateTime пользователя
                                .toLocalDate(),
                        TreeMap::new, Collectors.toList()));

        int normCalories = profile.getCaloriesNormPerDay() != null ? profile.getCaloriesNormPerDay() : 0;

        sb.append("📊 <b>ЕЖЕНЕДЕЛЬНЫЙ ОТЧЕТ ПИТАНИЯ</b>\n");
        sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n\n");

        int successfulDays = 0;
        int totalCalories = 0, totalP = 0, totalF = 0, totalC = 0;

        for (Map.Entry<LocalDate, List<Dish>> entry : byDay.entrySet()) {
            LocalDate day = entry.getKey();
            List<Dish> dayDishes = entry.getValue();

            int dayCals = 0, dayP = 0, dayF = 0, dayC = 0;

            sb.append("📅 <b>").append(day).append("</b>\n");

            for (Dish dish : dayDishes) {
                sb.append("▫️ <i>").append(dish.getName()).append("</i>\n");
                // Детализация блюда: Ккал и БЖУ без смайликов
                sb.append("  └ ").append(dish.getCalories()).append(" ккал | ")
                        .append("Б:").append(dish.getProteins()).append(" ")
                        .append("Ж:").append(dish.getFats()).append(" ")
                        .append("У:").append(dish.getCarbohydrates()).append("\n");

                dayCals += dish.getCalories();
                dayP += dish.getProteins();
                dayF += dish.getFats();
                dayC += dish.getCarbohydrates();
            }

            boolean isExceeded = normCalories > 0 && dayCals > normCalories;
            String dayStatusEmoji = isExceeded ? "🔴" : "🟢";
            if (!isExceeded) successfulDays++;

            sb.append("<b>").append(dayStatusEmoji).append(" Итого за день:</b>\n");
            sb.append("🔥 ").append(dayCals).append(" / ").append(normCalories).append(" ккал");

            if (isExceeded) {
                sb.append(" ⚠️ (+").append(dayCals - normCalories).append(")");
            }

            sb.append("\n💪 Б: ").append(dayP).append(" | 🥑 Ж: ").append(dayF).append(" | 🌾 У: ").append(dayC)
                    .append("\n\n");

            totalCalories += dayCals;
            totalP += dayP; totalF += dayF; totalC += dayC;
        }

        // Блок итоговой статистики за весь период
        sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
        sb.append("📈 <b>ИТОГИ ПЕРИОДА</b>\n\n");

        sb.append("✅ Дисциплина (калории): <b>").append(successfulDays).append(" из ").append(byDay.size()).append(" дн.</b>\n");

        if (!byDay.isEmpty()) {
            sb.append("🧮 Среднее потребление: <b>").append(totalCalories / byDay.size()).append(" ккал/день</b>\n");
        }

        sb.append("\n<b>Суммарный баланс КБЖУ:</b>\n")
                .append("🔥 ").append(totalCalories).append(" ккал\n")
                .append("💪 Б: ").append(totalP).append("г | ")
                .append("🥑 Ж: ").append(totalF).append("г | ")
                .append("🌾 У: ").append(totalC).append("г\n\n");

        String macroStatus = checkMacroBalance(totalP, totalC, profile, byDay.size());
        sb.append("📝 <b>Вердикт:</b> ").append(macroStatus);

        return sb.toString();
    }

    private String checkMacroBalance(int p, int c, UserNutritionProfile profile, int days) {
        if (days == 0 || profile.getProteinsNormGramsPerDay() == null) return "Недостаточно данных для анализа БЖУ.";

        int avgP = p / days;
        int normP = profile.getProteinsNormGramsPerDay();

        if (avgP < normP * 0.8) return "Старайтесь добирать норму белка для поддержки мышц. 💪";
        if (avgP > normP * 1.2) return "У вас отличный фокус на белок! Соблюдайте баланс. ✨";

        return "Рацион сбалансирован, вы отлично справляетесь! 🎯";
    }

//    private String getDishesString(List<Dish> dishes) {
//        StringBuilder sb = new StringBuilder();
//
//        // группируем по дате
//        Map<LocalDate, List<Dish>> byDay = dishes.stream()
//                .collect(Collectors.groupingBy(d -> d.getCreated().toLocalDate(),
//                        TreeMap::new, Collectors.toList()));
//
//        int totalCalories = 0, totalProteins = 0, totalFats = 0, totalCarbs = 0;
//
//        for (Map.Entry<LocalDate, List<Dish>> entry : byDay.entrySet()) {
//            LocalDate day = entry.getKey();
//            List<Dish> dayDishes = entry.getValue();
//
//            sb.append("📅 <b>").append(day).append("</b>\n");
//
//            int dayCalories = 0, dayProteins = 0, dayFats = 0, dayCarbs = 0;
//
//            for (Dish dish : dayDishes) {
//                sb.append("🍽 <b>").append(dish.getName()).append("</b> ")
//                        .append("🔥 ").append(dish.getCalories()).append("ккал ")
//                        .append("💪 ").append(dish.getProteins()).append("Б ")
//                        .append("🥑 ").append(dish.getFats()).append("Ж ")
//                        .append("🌾 ").append(dish.getCarbohydrates()).append("У\n");
//
//                dayCalories += dish.getCalories();
//                dayProteins += dish.getProteins();
//                dayFats += dish.getFats();
//                dayCarbs += dish.getCarbohydrates();
//            }
//
//            sb.append("<b>— Итого за день: </b>")
//                    .append("🔥 ").append(dayCalories).append(" ккал ")
//                    .append("💪 ").append(dayProteins).append(" Б ")
//                    .append("🥑 ").append(dayFats).append(" Ж ")
//                    .append("🌾 ").append(dayCarbs).append(" У\n\n");
//
//            totalCalories += dayCalories;
//            totalProteins += dayProteins;
//            totalFats += dayFats;
//            totalCarbs += dayCarbs;
//        }
//
//        sb.append("📊 <b>Итого за 7 дней:</b>\n")
//                .append("🔥 ").append(totalCalories).append("ккал ")
//                .append("💪 ").append(totalProteins).append("Б ")
//                .append("🥑 ").append(totalFats).append("Ж ")
//                .append("🌾 ").append(totalCarbs).append("У");
//
//        return sb.toString().trim();
//
//    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEEK_LIST.getCommandText();
    }
}
