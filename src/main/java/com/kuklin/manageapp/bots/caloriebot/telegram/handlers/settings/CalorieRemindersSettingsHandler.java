package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.texts.SettingsTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Обработчик настройки уведомлений/напоминаний
 */
@Component
@RequiredArgsConstructor
public class CalorieRemindersSettingsHandler implements CalorieSettingsHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserSettingsService userSettingsService;
    private final CalorieTexts texts;
    private final SettingsTexts settingsTexts;
    //Команда переключения булевых параметров
    private static final String TOGGLE_CMD = "TOGGLE";
    //Команды для идентификации уведомлений
    private static final String DAILY = "DAILY";
    private static final String MEAL = "MEAL";
    @Override
    public String getLabelKey() {
        return BotTextKeys.SETTINGS_BUTTON_REMINDERS;
    }

    //Ожидаю только callback
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Message message = TelegramBot.getCallbackMessage(query);
        Long chatId = message.getChatId();
        String data = query.getData();
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());

        //Получаем настройки пользователя
        UserSettings settings =
                userSettingsService.getOrCreate(telegramUser.getAppUserId());

        //Если калбэк пришел с начальными данными
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    settingsTexts.buildRemindersText(settings, language),
                    message.getMessageId(),
                    buildReminderKeyboard(language)
            );
            return;
        }

        //Разбиваем данные колбэка на части
        String[] cmd = data.split(TelegramBot.DEFAULT_DELIMETER);
        if (cmd.length < 2) return;

        //Обработка команд настройки напоминаний о приеме пищи
        if (MEAL.equals(cmd[1])) {
            handleMeal(query, telegramUser, settings, language);
            return;
        }

        //Обработка команд настройки ежедневных отчетов
        if (DAILY.equals(cmd[1])) {
            handleDaily(query, telegramUser, settings, cmd, language);
        }
    }


    private void handleDaily(
            CallbackQuery query,
            TelegramUser user,
            UserSettings userSettings,
            String[] cmd,
            AppLanguage language
    ) {
        Long chatId = query.getMessage().getChatId();

        int hour = userSettings.getDailySummaryHour();
        boolean enabled = userSettings.isDailySummaryEnabled();

        if (cmd.length >= 4) {
            hour = Integer.parseInt(cmd[3]);
            enabled = "ON".equals(cmd[4]);
        }

        if (cmd.length >= 3) {
            switch (cmd[2]) {
                case "INC" -> hour = (hour + 1) % 24;
                case "DEC" -> hour = (hour + 23) % 24;
                case "TOGGLE" -> enabled = !enabled;
                case "SAVE" -> {
                    userSettingsService.setDailySummaryTimeOrNull(user.getAppUserId(), hour);
                    if (enabled)
                        userSettingsService.enableDailySummaryOrNull(user.getAppUserId());
                    else
                        userSettingsService.disableDailySummary(user.getAppUserId());

                    calorieTelegramBot.sendEditMessage(
                            chatId,
                            settingsTexts.buildRemindersText(userSettings, language),
                            query.getMessage().getMessageId(),
                            buildReminderKeyboard(language)
                    );
                    return;
                }
            }
        }

        calorieTelegramBot.sendEditMessage(
                chatId,
                settingsTexts.buildDailySummaryText(userSettings, language, false),
                query.getMessage().getMessageId(),
                buildDailyKeyboard(hour, enabled, language)
        );
    }

    private void handleMeal(
            CallbackQuery query,
            TelegramUser user,
            UserSettings userSettings,
            AppLanguage language
    ) {
        if (userSettings.isMealReminderEnabled()) {
            userSettingsService.disableMealReminder(user.getAppUserId());
        } else {
            userSettingsService.enableMealReminderOrNull(user.getAppUserId());
        }

        UserSettings updated =
                userSettingsService.getOrCreate(user.getAppUserId());

        calorieTelegramBot.sendEditMessage(
                query.getMessage().getChatId(),
                settingsTexts.buildMealReminderText(userSettings, language),
                query.getMessage().getMessageId(),
                buildMealKeyboard(updated, language)
        );
    }

    private InlineKeyboardMarkup buildMealKeyboard(UserSettings s, AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        String stateText = texts.get(language, s.isMealReminderEnabled()
                ? BotTextKeys.COMMON_BUTTON_ON
                : BotTextKeys.COMMON_BUTTON_OFF);

        builder.row(
                TelegramKeyboard.button(
                        stateText,
                        getHandlerListName()
                                + TelegramBot.DEFAULT_DELIMETER + MEAL
                                + TelegramBot.DEFAULT_DELIMETER + TOGGLE_CMD
                )
        );

        builder.row(
                TelegramKeyboard.button(getBackLabel(language), getHandlerListName())
        );

        return builder.build();
    }

    private InlineKeyboardMarkup buildReminderKeyboard(AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder b = TelegramKeyboard.builder();

        String cb = getHandlerListName() + TelegramBot.DEFAULT_DELIMETER;

        b.row(TelegramKeyboard.button(texts.get(language, BotTextKeys.SETTINGS_BUTTON_DAILY), cb + DAILY));
        b.row(TelegramKeyboard.button(texts.get(language, BotTextKeys.SETTINGS_BUTTON_MEAL), cb + MEAL));
        b.row(TelegramKeyboard.button(getBackLabel(language), Command.CALORIE_SETTINGS.getCommandText()));

        return b.build();
    }

    private InlineKeyboardMarkup buildDailyKeyboard(int hour, boolean enabled, AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder b = TelegramKeyboard.builder();

        String base = getHandlerListName()
                + TelegramBot.DEFAULT_DELIMETER + DAILY
                + TelegramBot.DEFAULT_DELIMETER;

        String state = enabled ? "ON" : "OFF";

        b.row(
                TelegramKeyboard.button("➖", base + "DEC" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state),
                TelegramKeyboard.button(
                        String.format("%02d:00", hour),
                        "IGNORE"
                ),
                TelegramKeyboard.button("➕", base + "INC" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state)
        );

        b.row(
                TelegramKeyboard.button(
                        texts.get(language, enabled ? BotTextKeys.COMMON_BUTTON_ON : BotTextKeys.COMMON_BUTTON_OFF),
                        base + "TOGGLE" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state
                )
        );

        b.row(
                TelegramKeyboard.button(
                        "💾 " + texts.get(language, BotTextKeys.COMMON_BUTTON_SAVE),
                        base + "SAVE" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state
                )
        );

        b.row(
                TelegramKeyboard.button(getBackLabel(language), getHandlerListName())
        );

        return b.build();
    }

    /** «⬅ Назад» / «⬅ Back». */
    private String getBackLabel(AppLanguage language) {
        return "⬅ " + texts.get(language, BotTextKeys.COMMON_BUTTON_BACK);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS_REMINDERS.getCommandText();
    }
}
