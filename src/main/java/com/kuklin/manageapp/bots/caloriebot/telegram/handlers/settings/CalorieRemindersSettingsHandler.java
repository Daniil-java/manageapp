package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.settings;

import com.kuklin.manageapp.bots.caloriebot.entities.UserSettings;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
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
    //Команда переключения булевых параметров
    private static final String TOGGLE_CMD = "TOGGLE";
    //Команды для идентификации уведомлений
    private static final String DAILY = "DAILY";
    private static final String MEAL = "MEAL";
    @Override
    public String getLabel() {
        return "⏰Напоминания";
    }

    //Ожидаю только callback
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Message message = query.getMessage();
        Long chatId = message.getChatId();
        String data = query.getData();

        //Получаем настройки пользователя
        UserSettings settings =
                userSettingsService.getOrCreate(telegramUser.getTelegramId());

        //Если калбэк пришел с начальными данными
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    getRemindersStatusText(settings),
                    message.getMessageId(),
                    buildReminderKeyboard()
            );
            return;
        }

        //Разбиваем данные колбэка на части
        String[] cmd = data.split(TelegramBot.DEFAULT_DELIMETER);
        if (cmd.length < 2) return;

        //Обработка команд настройки напоминаний о приеме пищи
        if (MEAL.equals(cmd[1])) {
            handleMeal(query, telegramUser, settings);
            return;
        }

        //Обработка команд настройки ежедневных отчетов
        if (DAILY.equals(cmd[1])) {
            handleDaily(query, telegramUser, settings, cmd);
        }
    }

    //Текстовка для статуса настроек
    private String getRemindersStatusText(UserSettings s) {
        StringBuilder sb = new StringBuilder();

        // общий флаг
        sb.append(
                s.isRemindersEnabled()
                        ? "🔔 Напоминания: 🟢 включены"
                        : "🔕 Напоминания: 🔴 выключены"
        );

        sb.append("\n\n");
        sb.append(getDailySummaryText(s));
        sb.append("\n\n");
        sb.append(getMealReminderText(s));

        return sb.toString();
    }

    private void handleDaily(
            CallbackQuery query,
            TelegramUser user,
            UserSettings userSettings,
            String[] cmd
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
                    userSettingsService.setDailySummaryTimeOrNull(user.getTelegramId(), hour);
                    if (enabled)
                        userSettingsService.enableDailySummaryOrNull(user.getTelegramId());
                    else
                        userSettingsService.disableDailySummary(user.getTelegramId());

                    calorieTelegramBot.sendEditMessage(
                            chatId,
                            getRemindersStatusText(userSettings),
                            query.getMessage().getMessageId(),
                            buildReminderKeyboard()
                    );
                    return;
                }
            }
        }

        calorieTelegramBot.sendEditMessage(
                chatId,
                getDailySummaryText(userSettings),
                query.getMessage().getMessageId(),
                buildDailyKeyboard(hour, enabled)
        );
    }

    private String getDailySummaryText(UserSettings userSettings) {
        if (!userSettings.isDailySummaryEnabled()) {
            return "📊 Итоги дня: 🔴 выключены";
        }

        int hour = userSettings.getDailySummaryHour();
        String time = String.format("%02d:00", hour);

        return "📊 Итоги дня: 🟢 включены\n" +
                "⏰ Время: " + time;
    }

    private void handleMeal(
            CallbackQuery query,
            TelegramUser user,
            UserSettings userSettings
    ) {
        if (userSettings.isMealReminderEnabled()) {
            userSettingsService.disableMealReminder(user.getTelegramId());
        } else {
            userSettingsService.enableMealReminderOrNull(user.getTelegramId());
        }

        UserSettings updated =
                userSettingsService.getOrCreate(user.getTelegramId());

        calorieTelegramBot.sendEditMessage(
                query.getMessage().getChatId(),
                getMealReminderText(userSettings),
                query.getMessage().getMessageId(),
                buildMealKeyboard(updated)
        );
    }

    private String getMealReminderText(UserSettings userSettings) {
        if (!userSettings.isMealReminderEnabled()) {
            return "🍽 Напоминания о еде: 🔴 выключены";
        }

        int minutes = userSettings.getMealReminderIntervalMinutes();
        int hours = minutes / 60;

        String intervalText = minutes % 60 == 0
                ? hours + " ч"
                : minutes + " мин";

        return "🍽 Напоминания о еде: 🟢 включены\n" +
                "⏳ Интервал без еды: " + intervalText;
    }

    private InlineKeyboardMarkup buildMealKeyboard(UserSettings s) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        String stateText = s.isMealReminderEnabled()
                ? "🟢 ВКЛ"
                : "🔴 ВЫКЛ";

        builder.row(
                TelegramKeyboard.button(
                        stateText,
                        getHandlerListName()
                                + TelegramBot.DEFAULT_DELIMETER + MEAL
                                + TelegramBot.DEFAULT_DELIMETER + TOGGLE_CMD
                )
        );

        builder.row(
                TelegramKeyboard.button("⬅ Назад", getHandlerListName())
        );

        return builder.build();
    }

    private InlineKeyboardMarkup buildReminderKeyboard() {
        TelegramKeyboard.TelegramKeyboardBuilder b = TelegramKeyboard.builder();

        String cb = getHandlerListName() + TelegramBot.DEFAULT_DELIMETER;

        b.row(TelegramKeyboard.button("📊 Итоги дня", cb + DAILY));
        b.row(TelegramKeyboard.button("🍽 Напоминания о еде", cb + MEAL));
        b.row(TelegramKeyboard.button("⬅ Назад", Command.CALORIE_SETTINGS.getCommandText()));

        return b.build();
    }

    private InlineKeyboardMarkup buildDailyKeyboard(int hour, boolean enabled) {
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
                        enabled ? "🟢 ВКЛ" : "🔴 ВЫКЛ",
                        base + "TOGGLE" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state
                )
        );

        b.row(
                TelegramKeyboard.button(
                        "💾 Сохранить",
                        base + "SAVE" + TelegramBot.DEFAULT_DELIMETER + hour + TelegramBot.DEFAULT_DELIMETER + state
                )
        );

        b.row(
                TelegramKeyboard.button("⬅ Назад", getHandlerListName())
        );

        return b.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_SETTINGS_REMINDERS.getCommandText();
    }
}
