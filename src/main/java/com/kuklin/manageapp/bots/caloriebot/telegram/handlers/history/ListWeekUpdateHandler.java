package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
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
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes = dishService.getWeekDishes(telegramUser.getAppUserId());
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                getWeeklyAnalytics(dishes, profile, telegramUser),
                menuButtons.buildKeyboard(telegramUser.getAppUserId()),
                null
        );
    }

    // Калории и граммы передаём строками: ICU иначе поставил бы разделители разрядов (1 850)
    private String getWeeklyAnalytics(List<Dish> dishes, UserNutritionProfile profile, TelegramUser telegramUser) {
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        StringBuilder sb = new StringBuilder();

        // Группируем по дате
        ZoneId userZone = userSettingsService.getOrCreate(telegramUser.getAppUserId()).getZoneId();

        Map<LocalDate, List<Dish>> byDay = dishes.stream()
                .collect(Collectors.groupingBy(d -> d.getCreated()
                                .atZone(userZone) // Переводим Instant в ZonedDateTime пользователя
                                .toLocalDate(),
                        TreeMap::new, Collectors.toList()));

        int normCalories = profile.getCaloriesNormPerDay() != null ? profile.getCaloriesNormPerDay() : 0;

        sb.append(texts.get(language, BotTextKeys.WEEK_TITLE)).append("\n");
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
                sb.append("  ").append(texts.get(language, BotTextKeys.WEEK_DISH_MACROS,
                        String.valueOf(dish.getCalories()), String.valueOf(dish.getProteins()),
                        String.valueOf(dish.getFats()), String.valueOf(dish.getCarbohydrates()))).append("\n");

                dayCals += dish.getCalories();
                dayP += dish.getProteins();
                dayF += dish.getFats();
                dayC += dish.getCarbohydrates();
            }

            boolean isExceeded = normCalories > 0 && dayCals > normCalories;
            String dayStatusEmoji = isExceeded ? "🔴" : "🟢";
            if (!isExceeded) successfulDays++;

            sb.append(texts.get(language, BotTextKeys.WEEK_DAY_TOTAL, dayStatusEmoji)).append("\n");
            sb.append(texts.get(language, BotTextKeys.WEEK_DAY_CALORIES,
                    String.valueOf(dayCals), String.valueOf(normCalories)));

            if (isExceeded) {
                sb.append(" ⚠️ (+").append(dayCals - normCalories).append(")");
            }

            sb.append("\n").append(texts.get(language, BotTextKeys.WEEK_DAY_MACROS,
                    String.valueOf(dayP), String.valueOf(dayF), String.valueOf(dayC))).append("\n\n");

            totalCalories += dayCals;
            totalP += dayP; totalF += dayF; totalC += dayC;
        }

        // Блок итоговой статистики за весь период
        sb.append("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯\n");
        sb.append(texts.get(language, BotTextKeys.WEEK_SUMMARY_TITLE)).append("\n\n");

        // Число дней — числом: в английском от него зависит «day» / «days»
        sb.append(texts.get(language, BotTextKeys.WEEK_DISCIPLINE, successfulDays, byDay.size())).append("\n");

        if (!byDay.isEmpty()) {
            sb.append(texts.get(language, BotTextKeys.WEEK_AVERAGE, String.valueOf(totalCalories / byDay.size())))
                    .append("\n");
        }

        sb.append("\n").append(texts.get(language, BotTextKeys.WEEK_BALANCE,
                String.valueOf(totalCalories), String.valueOf(totalP), String.valueOf(totalF), String.valueOf(totalC)))
                .append("\n\n");

        String macroStatus = checkMacroBalance(totalP, profile, byDay.size(), language);
        sb.append(texts.get(language, BotTextKeys.WEEK_VERDICT, macroStatus));

        return sb.toString();
    }

    private String checkMacroBalance(int p, UserNutritionProfile profile, int days, AppLanguage language) {
        if (days == 0 || profile.getProteinsNormGramsPerDay() == null) {
            return texts.get(language, BotTextKeys.WEEK_VERDICT_NO_DATA);
        }

        int avgP = p / days;
        int normP = profile.getProteinsNormGramsPerDay();

        if (avgP < normP * 0.8) return texts.get(language, BotTextKeys.WEEK_VERDICT_LOW_PROTEIN);
        if (avgP > normP * 1.2) return texts.get(language, BotTextKeys.WEEK_VERDICT_HIGH_PROTEIN);

        return texts.get(language, BotTextKeys.WEEK_VERDICT_BALANCED);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEEK_LIST.getCommandText();
    }
}
