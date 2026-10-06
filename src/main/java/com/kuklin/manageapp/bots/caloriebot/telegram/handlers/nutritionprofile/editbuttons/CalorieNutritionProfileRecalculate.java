package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.UserNutritionProfileDto;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.CalorieNutritionProfileEditCallbackUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/*
 * Режим нормы калорий в редактировании профиля: «Автопересчёт» / «Без пересчёта».
 *
 * /recalculate AUTO     — включить автопересчёт: при ручной норме сначала спрашиваем (норма поменяется),
 *                         при уже включённом — просто пересчитываем
 * /recalculate AUTO_YES — подтверждение: норма по формуле, автопересчёт включён
 * /recalculate MANUAL   — выключить автопересчёт: текущие числа остаются, подтверждение не нужно
 *
 * Всё — редактированием того же сообщения; после действия возвращается сообщение редактирования профиля.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieNutritionProfileRecalculate implements CalorieBotUpdateHandler {
    public static final String AUTO = "AUTO";
    public static final String AUTO_YES = "AUTO_YES";
    public static final String MANUAL = "MANUAL";

    private static final String FORMULA_NEEDS_PROFILE =
            "Для расчёта по формуле заполните пол, возраст, рост, вес, активность и цель.";

    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileEditCallbackUpdateHandler calorieNutritionProfileEditCallbackUpdateHandler;
    private final CalorieTelegramBot calorieTelegramBot;

    public static String callback(String action) {
        return Command.CALORIE_PROFILE_RECALCULATE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + action;
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getAppUserId());
        // Старые сообщения с кнопкой «Пересчитать нормы» присылают команду без действия — это включение автопересчёта
        String action = extractActionOrDefault(update.getCallbackQuery().getData());

        try {
            switch (action) {
                case MANUAL -> {
                    if (!profile.isManualNorm()) userNutritionProfileService.switchToManualNorm(profile);
                }
                case AUTO_YES -> userNutritionProfileService.switchToAutoNorm(profile);
                default -> {
                    if (profile.isManualNorm()) {
                        askAutoConfirmation(update, profile);
                        return;
                    }
                    // Автопересчёт уже включён — просто пересчитать
                    userNutritionProfileService.switchToAutoNorm(profile);
                }
            }
        } catch (InsufficientProfileDataException e) {
            editWithBackButton(update, FORMULA_NEEDS_PROFILE);
            return;
        } catch (UserNutritionProfileValidationException e) {
            editWithBackButton(update, e.getMessage());
            return;
        }
        calorieNutritionProfileEditCallbackUpdateHandler.sendMainMessage(update, telegramUser);
    }

    // Предупреждение: КБЖУ будут пересчитаны по формуле — показываем, что именно поменяется
    private void askAutoConfirmation(Update update, UserNutritionProfile profile) {
        UserNutritionProfileDto formula;
        try {
            formula = userNutritionProfileService.previewAutoNorm(profile);
        } catch (InsufficientProfileDataException e) {
            editWithBackButton(update, FORMULA_NEEDS_PROFILE);
            return;
        }

        String text = "Включить автопересчёт?\n\n"
                + "КБЖУ будут пересчитаны по формуле, ваша норма калорий заменится. "
                + "Дальше норма будет меняться вместе с весом, активностью и целью.\n\n"
                + diffLine("Калории", profile.getCaloriesNormPerDay(), formula.getCaloriesNormPerDay(), "ккал")
                + diffLine("Белки", profile.getProteinsNormGramsPerDay(), formula.getProteinsNormGramsPerDay(), "г")
                + diffLine("Жиры", profile.getFatsNormGramsPerDay(), formula.getFatsNormGramsPerDay(), "г")
                + diffLine("Углеводы", profile.getCarbsNormGramsPerDay(), formula.getCarbsNormGramsPerDay(), "г");

        InlineKeyboardMarkup keyboard = TelegramKeyboard.builder().row(
                TelegramKeyboard.button("Да, пересчитать", callback(AUTO_YES)),
                TelegramKeyboard.button("Нет", Command.CALORIE_PROFILE_EDIT.getCommandText())
        ).build();

        edit(update, text, keyboard);
    }

    private static String diffLine(String label, Integer was, Integer will, String unit) {
        String wasText = was == null ? "—" : was.toString();
        return "• " + label + ": " + wasText + " → " + will + " " + unit + "\n";
    }

    private void editWithBackButton(Update update, String text) {
        edit(update, text, TelegramKeyboard.builder().row(
                TelegramKeyboard.button("Вернуться", Command.CALORIE_PROFILE_EDIT.getCommandText())
        ).build());
    }

    private void edit(Update update, String text, InlineKeyboardMarkup keyboard) {
        Message message = update.getCallbackQuery().getMessage();
        calorieTelegramBot.sendEditMessage(message.getChatId(), text, message.getMessageId(), keyboard);
    }

    private static String extractActionOrDefault(String data) {
        String[] parts = data == null ? new String[0] : data.split(TelegramBot.DEFAULT_DELIMETER);
        return parts.length > 1 ? parts[1] : AUTO;
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PROFILE_RECALCULATE.getCommandText();
    }
}
