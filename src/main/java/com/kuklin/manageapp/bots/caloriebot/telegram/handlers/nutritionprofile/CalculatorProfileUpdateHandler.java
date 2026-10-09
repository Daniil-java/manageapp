package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.landing.CalculatorStartParam;
import com.kuklin.manageapp.bots.caloriebot.models.landing.CalculatorStartParam.Calculator;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.math.BigDecimal;

/*
 * Профиль из калькулятора лендинга (кнопка «Вести дневник с этой нормой» → /start p_…).
 *
 * Отвечает за:
 * - пустой профиль — заполнить сразу и показать норму;
 * - заполненный — спросить, обновить ли его данными с сайта;
 * - колбэк ответа: «/calcapply p_…» — обновить, «/calcapply skip» — оставить как есть.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculatorProfileUpdateHandler implements CalorieBotUpdateHandler {
    private static final String SKIP = "skip";

    private final CalorieTelegramBot calorieTelegramBot;
    private final UserNutritionProfileService userNutritionProfileService;

    /** Вызывается из /start, когда в параметре пришли данные калькулятора. */
    public void offer(long chatId, Long userId, Calculator calculator) {
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        if (!profile.checkTargetCalculateParams()) {
            profile = userNutritionProfileService.applyCalculator(userId, calculator);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    "Перенёс данные из калькулятора — профиль заполнен ✅\n\n" + normText(profile),
                    profileKeyboard(), null);
            return;
        }

        if (calculator.sameAs(profile)) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    "Профиль уже совпадает с калькулятором 👌\n\n" + normText(profile),
                    profileKeyboard(), null);
            return;
        }

        calorieTelegramBot.sendReturnedMessage(chatId, questionText(profile, calculator), questionKeyboard(calculator), null);
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        CallbackQuery query = update.getCallbackQuery();
        long chatId = query.getMessage().getChatId();
        int messageId = query.getMessage().getMessageId();

        String[] parts = query.getData().split(TelegramBot.DEFAULT_DELIMETER, 2);
        String arg = parts.length > 1 ? parts[1] : "";

        if (SKIP.equals(arg)) {
            calorieTelegramBot.sendEditMessage(chatId, "Оставил профиль как есть 👌", messageId, profileKeyboard());
            return;
        }

        Calculator calculator = CalculatorStartParam.parse(arg).calculator();
        if (calculator == null) {
            log.warn("Broken calculator callback: {}", query.getData());
            calorieTelegramBot.sendEditMessage(chatId,
                    "Не получилось прочитать данные калькулятора. Профиль можно заполнить вручную 👇",
                    messageId, profileKeyboard());
            return;
        }

        UserNutritionProfile profile = userNutritionProfileService.applyCalculator(telegramUser.getAppUserId(), calculator);
        calorieTelegramBot.sendEditMessage(chatId,
                "Обновил профиль данными из калькулятора ✅\n\n" + normText(profile),
                messageId, profileKeyboard());
    }

    private String questionText(UserNutritionProfile profile, Calculator calculator) {
        StringBuilder sb = new StringBuilder("На сайте ты посчитал норму");
        Integer siteCalories = previewCalories(profile, calculator);
        if (siteCalories != null) {
            sb.append(" <b>").append(siteCalories).append(" ккал</b>");
        }
        sb.append(", а сейчас в профиле <b>").append(profile.getCaloriesNormPerDay()).append(" ккал</b>.\n\n");
        sb.append("С сайта: ").append(calculator.sex().getLabel().toLowerCase())
                .append(", ").append(calculator.ageYears()).append(" лет")
                .append(", ").append(calculator.heightCm()).append(" см")
                .append(", ").append(calculator.weightKg()).append(" кг")
                .append(", активность — ").append(calculator.activityLevel().getLabel().toLowerCase())
                .append(", цель — ").append(calculator.goal().getLabel().toLowerCase()).append(".\n\n");
        if (profile.isManualNorm()) {
            sb.append("Сейчас у тебя своя норма калорий — она заменится расчётной.\n\n");
        }
        return sb.append("Обновить профиль этими данными?").toString();
    }

    private Integer previewCalories(UserNutritionProfile profile, Calculator calculator) {
        try {
            return userNutritionProfileService.previewAutoNorm(profile.copy()
                    .setSex(calculator.sex())
                    .setAgeYears(calculator.ageYears())
                    .setHeightCm(calculator.heightCm())
                    .setCurrentWeightKg(BigDecimal.valueOf(calculator.weightKg()))
                    .setActivityLevel(calculator.activityLevel())
                    .setGoal(calculator.goal())).getCaloriesNormPerDay();
        } catch (InsufficientProfileDataException e) {
            return null;
        }
    }

    private static String normText(UserNutritionProfile profile) {
        return "🎯 Норма на день: <b>" + profile.getCaloriesNormPerDay() + " ккал</b>\n"
                + "Б " + profile.getProteinsNormGramsPerDay() + " г · Ж " + profile.getFatsNormGramsPerDay()
                + " г · У " + profile.getCarbsNormGramsPerDay() + " г\n"
                + "💧 Вода: " + profile.getWaterTargetMlPerDay() + " мл";
    }

    private InlineKeyboardMarkup questionKeyboard(Calculator calculator) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button("✅ Обновить", callback(calculator.encode())))
                .row(TelegramKeyboard.button("Оставить как есть", callback(SKIP)))
                .build();
    }

    private static InlineKeyboardMarkup profileKeyboard() {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button("✏️ Изменить профиль", Command.CALORIE_PROFILE_EDIT.getCommandText()))
                .build();
    }

    private String callback(String arg) {
        return getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + arg;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_FROM_CALCULATOR.getCommandText();
    }
}
