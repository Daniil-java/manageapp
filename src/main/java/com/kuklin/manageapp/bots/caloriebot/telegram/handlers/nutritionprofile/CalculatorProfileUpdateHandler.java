package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.nutritionprofile;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.components.services.exceptions.InsufficientProfileDataException;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
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
    private final CalorieTexts texts;

    /** Вызывается из /start, когда в параметре пришли данные калькулятора. */
    public void offer(long chatId, Long userId, Calculator calculator) {
        AppLanguage language = texts.languageOf(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);

        if (!profile.checkTargetCalculateParams()) {
            profile = userNutritionProfileService.applyCalculator(userId, calculator);
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(language, BotTextKeys.CALC_APPLIED) + "\n\n" + normText(profile, language),
                    profileKeyboard(language), null);
            return;
        }

        if (calculator.sameAs(profile)) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.get(language, BotTextKeys.CALC_SAME) + "\n\n" + normText(profile, language),
                    profileKeyboard(language), null);
            return;
        }

        calorieTelegramBot.sendReturnedMessage(chatId,
                questionText(profile, calculator, language), questionKeyboard(calculator, language), null);
    }

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        CallbackQuery query = update.getCallbackQuery();
        long chatId = query.getMessage().getChatId();
        int messageId = query.getMessage().getMessageId();

        String[] parts = query.getData().split(TelegramBot.DEFAULT_DELIMETER, 2);
        String arg = parts.length > 1 ? parts[1] : "";

        if (SKIP.equals(arg)) {
            calorieTelegramBot.sendEditMessage(chatId, texts.get(language, BotTextKeys.CALC_SKIPPED), messageId,
                    profileKeyboard(language));
            return;
        }

        Calculator calculator = CalculatorStartParam.parse(arg).calculator();
        if (calculator == null) {
            log.warn("Broken calculator callback: {}", query.getData());
            calorieTelegramBot.sendEditMessage(chatId, texts.get(language, BotTextKeys.CALC_BROKEN),
                    messageId, profileKeyboard(language));
            return;
        }

        UserNutritionProfile profile = userNutritionProfileService.applyCalculator(telegramUser.getAppUserId(), calculator);
        calorieTelegramBot.sendEditMessage(chatId,
                texts.get(language, BotTextKeys.CALC_UPDATED) + "\n\n" + normText(profile, language),
                messageId, profileKeyboard(language));
    }

    // Калории строками — без разделителей разрядов; возраст числом — от него зависит «год / года / лет»
    private String questionText(UserNutritionProfile profile, Calculator calculator, AppLanguage language) {
        StringBuilder sb = new StringBuilder();
        Integer siteCalories = previewCalories(profile, calculator);
        String profileCalories = String.valueOf(profile.getCaloriesNormPerDay());
        sb.append(siteCalories != null
                ? texts.get(language, BotTextKeys.CALC_QUESTION_NORM, String.valueOf(siteCalories), profileCalories)
                : texts.get(language, BotTextKeys.CALC_QUESTION_NORM_UNKNOWN, profileCalories)).append("\n\n");
        sb.append(texts.get(language, BotTextKeys.CALC_QUESTION_DATA,
                lowerLabel(calculator.sex(), language),
                calculator.ageYears(),
                String.valueOf(calculator.heightCm()),
                String.valueOf(calculator.weightKg()),
                lowerLabel(calculator.activityLevel(), language),
                lowerLabel(calculator.goal(), language))).append("\n\n");
        if (profile.isManualNorm()) {
            sb.append(texts.get(language, BotTextKeys.CALC_QUESTION_MANUAL_NORM)).append("\n\n");
        }
        return sb.append(texts.get(language, BotTextKeys.CALC_QUESTION_ASK)).toString();
    }

    private String lowerLabel(UserNutritionProfile.Labeled value, AppLanguage language) {
        return texts.get(language, value.getTextKey()).toLowerCase(language.getLocale());
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

    private String normText(UserNutritionProfile profile, AppLanguage language) {
        return texts.get(language, BotTextKeys.CALC_NORM,
                String.valueOf(profile.getCaloriesNormPerDay()),
                String.valueOf(profile.getProteinsNormGramsPerDay()),
                String.valueOf(profile.getFatsNormGramsPerDay()),
                String.valueOf(profile.getCarbsNormGramsPerDay()),
                String.valueOf(profile.getWaterTargetMlPerDay()));
    }

    private InlineKeyboardMarkup questionKeyboard(Calculator calculator, AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.CALC_BUTTON_UPDATE), callback(calculator.encode())))
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.CALC_BUTTON_KEEP), callback(SKIP)))
                .build();
    }

    private InlineKeyboardMarkup profileKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.button(texts.get(language, BotTextKeys.PROFILE_BUTTON_EDIT),
                        Command.CALORIE_PROFILE_EDIT.getCommandText()))
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
