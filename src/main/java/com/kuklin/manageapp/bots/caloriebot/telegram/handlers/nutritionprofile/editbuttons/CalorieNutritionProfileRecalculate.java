package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile.editbuttons;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.validation.UserNutritionProfileValidationException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class CalorieNutritionProfileRecalculate implements CalorieBotUpdateHandler {
    public static final String AUTO = "AUTO";
    public static final String AUTO_YES = "AUTO_YES";
    public static final String MANUAL = "MANUAL";
    public static final String WATER_AUTO = "WATER_AUTO";
    public static final String WATER_AUTO_YES = "WATER_AUTO_YES";

    private final UserNutritionProfileService userNutritionProfileService;
    private final CalorieNutritionProfileEditCallbackUpdateHandler calorieNutritionProfileEditCallbackUpdateHandler;
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    public static String callback(String action) {
        return Command.CALORIE_PROFILE_RECALCULATE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + action;
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(telegramUser.getAppUserId());
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        // Старые сообщения с кнопкой «Пересчитать нормы» присылают команду без действия — это включение автопересчёта
        String action = extractActionOrDefault(update.getCallbackQuery().getData());

        try {
            switch (action) {
                case MANUAL -> {
                    if (!profile.isManualNorm()) userNutritionProfileService.switchToManualNorm(profile);
                }
                case AUTO_YES -> userNutritionProfileService.switchToAutoNorm(profile);
                case WATER_AUTO -> {
                    askWaterAutoConfirmation(update, profile, language);
                    return;
                }
                case WATER_AUTO_YES -> {
                    try {
                        userNutritionProfileService.switchToAutoWater(profile);
                    } catch (InsufficientProfileDataException e) {
                        editWithBackButton(update, texts.get(language, BotTextKeys.PROFILE_RECALC_WATER_NEEDS_PROFILE), language);
                        return;
                    }
                }
                default -> {
                    if (profile.isManualNorm()) {
                        askAutoConfirmation(update, profile, language);
                        return;
                    }
                    // Автопересчёт уже включён — просто пересчитать
                    userNutritionProfileService.switchToAutoNorm(profile);
                }
            }
        } catch (InsufficientProfileDataException e) {
            editWithBackButton(update, texts.get(language, BotTextKeys.PROFILE_RECALC_NEEDS_PROFILE), language);
            return;
        } catch (UserNutritionProfileValidationException e) {
            // Текст исключения — для логов; пользователю — общее «Некорректные данные!»
            log.warn("Profile validation failed on recalculation: {}", e.getMessage());
            editWithBackButton(update, texts.get(language, BotTextKeys.PROFILE_VALIDATION_ERROR), language);
            return;
        }
        calorieNutritionProfileEditCallbackUpdateHandler.sendMainMessage(update, telegramUser);
    }

    // Предупреждение: КБЖУ будут пересчитаны по формуле — показываем, что именно поменяется
    private void askAutoConfirmation(Update update, UserNutritionProfile profile, AppLanguage language) {
        UserNutritionProfileDto formula;
        try {
            formula = userNutritionProfileService.previewAutoNorm(profile);
        } catch (InsufficientProfileDataException e) {
            editWithBackButton(update, texts.get(language, BotTextKeys.PROFILE_RECALC_NEEDS_PROFILE), language);
            return;
        }

        String kcal = texts.get(language, BotTextKeys.COMMON_UNIT_KCAL);
        String gram = texts.get(language, BotTextKeys.COMMON_UNIT_GRAM);
        String text = texts.get(language, BotTextKeys.PROFILE_RECALC_ASK_AUTO) + "\n\n"
                + diffLine(language, BotTextKeys.PROFILE_LABEL_CALORIES, profile.getCaloriesNormPerDay(), formula.getCaloriesNormPerDay(), kcal)
                + diffLine(language, BotTextKeys.PROFILE_LABEL_PROTEINS, profile.getProteinsNormGramsPerDay(), formula.getProteinsNormGramsPerDay(), gram)
                + diffLine(language, BotTextKeys.PROFILE_LABEL_FATS, profile.getFatsNormGramsPerDay(), formula.getFatsNormGramsPerDay(), gram)
                + diffLine(language, BotTextKeys.PROFILE_LABEL_CARBS, profile.getCarbsNormGramsPerDay(), formula.getCarbsNormGramsPerDay(), gram);

        InlineKeyboardMarkup keyboard = TelegramKeyboard.builder().row(
                TelegramKeyboard.button(texts.get(language, BotTextKeys.PROFILE_RECALC_BUTTON_YES), callback(AUTO_YES)),
                TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_NO), Command.CALORIE_PROFILE_EDIT.getCommandText())
        ).build();

        edit(update, text, keyboard);
    }

    // Своя вода → по формуле: предупреждаем и показываем, какой станет норма.
    // «Нет» возвращает на экран «Норма воды», откуда пришли
    private void askWaterAutoConfirmation(Update update, UserNutritionProfile profile, AppLanguage language) {
        Integer formula = UserNutritionProfileService.calcFormulaWaterOrNull(profile);
        if (formula == null) {
            editWithBackButton(update, texts.get(language, BotTextKeys.PROFILE_RECALC_WATER_NEEDS_PROFILE), language);
            return;
        }

        String text = texts.get(language, BotTextKeys.PROFILE_RECALC_ASK_WATER) + "\n\n"
                + diffLine(language, BotTextKeys.PROFILE_LABEL_WATER, profile.getWaterTargetMlPerDay(), formula,
                texts.get(language, BotTextKeys.COMMON_UNIT_ML));

        InlineKeyboardMarkup keyboard = TelegramKeyboard.builder().row(
                TelegramKeyboard.button(texts.get(language, BotTextKeys.PROFILE_RECALC_BUTTON_YES_WATER), callback(WATER_AUTO_YES)),
                TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_NO), Command.CALORIE_PROFILE_EDIT.getCommandText()
                        + TelegramBot.DEFAULT_DELIMETER + ProfileEditAction.WATER_TARGET.getCode())
        ).build();

        edit(update, text, keyboard);
    }

    /** «• Калории: 1800 → 2100 ккал». Числа строками — без разделителей разрядов. */
    private String diffLine(AppLanguage language, String labelKey, Integer was, Integer will, String unit) {
        String wasText = was == null ? "—" : was.toString();
        return texts.get(language, BotTextKeys.PROFILE_RECALC_DIFF_LINE,
                texts.get(language, labelKey), wasText, String.valueOf(will), unit) + "\n";
    }

    private void editWithBackButton(Update update, String text, AppLanguage language) {
        edit(update, text, TelegramKeyboard.builder().row(
                TelegramKeyboard.button(texts.get(language, BotTextKeys.COMMON_BUTTON_RETURN),
                        Command.CALORIE_PROFILE_EDIT.getCommandText())
        ).build());
    }

    private void edit(Update update, String text, InlineKeyboardMarkup keyboard) {
        Message message = TelegramBot.getCallbackMessage(update.getCallbackQuery());
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
