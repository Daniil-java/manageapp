package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.history;

import com.kuklin.manageapp.bots.caloriebot.components.services.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.components.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishKeyboards;
import com.kuklin.manageapp.bots.caloriebot.texts.DishTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Обработчик для отображения статистики и списка блюд пользователя за текущий день.
 * Кнопками ◀ ▶ листаются прошлые дни (не дальше MAX_DAYS_BACK) — только просмотр, без удаления и карточки.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodayUpdateHandler implements CalorieBotUpdateHandler {
    private final UserSettingsService userSettingsService;
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private final DishTexts dishTexts;
    private final DishKeyboards dishKeyboards;
    private final CalorieTexts texts;
    private final CalorieMenuButtons menuButtons;

    // Константы для формирования callback-данных
    private static final String DELIMITER = TelegramBot.DEFAULT_DELIMETER;
    private static final String REMOVE_CMD = Command.CALORIE_TODAY_LIST.getCommandText() + DELIMITER + "r";
    private static final String GET_DISH_CMD = REMOVE_CMD + DELIMITER;
    // Прошлый день: "📊СЕГОДНЯ d 2026-10-21"
    private static final String DAY_CMD = Command.CALORIE_TODAY_LIST.getCommandText() + DELIMITER + "d" + DELIMITER;

    /** Сколько дней назад можно листать. */
    static final int MAX_DAYS_BACK = 30;

    /**
     * Основная точка входа для обработки обновлений.
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            handleCallback(update, telegramUser);
        } else {
            sendTodayMessage(update.getMessage().getChatId(), telegramUser.getAppUserId());
        }
    }

    /**
     * Логика обработки нажатий на инлайн-кнопки.
     */
    private void handleCallback(Update update, TelegramUser telegramUser) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long userId = telegramUser.getAppUserId();

        // Если нажата кнопка возврата к списку/обновления
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(chatId, getTodaySummaryText(userId), messageId, getStatsKeyboard(userId));
        }
        // Если выбрано конкретное блюдо из списка на удаление
        else if (data.startsWith(GET_DISH_CMD)) {
            handleDishDetailCallback(update, userId, data);
        }
        // Листание по дням
        else if (data.startsWith(DAY_CMD)) {
            showDay(chatId, messageId, userId, parseDayOrNull(data));
        }
        // В остальных случаях (например, переход к режиму удаления)
        else {
            sendRemoveMessage(update, telegramUser);
        }
    }

    /**
     * Обработка выбора конкретного блюда: получение информации о нем и весов порций.
     */
    private void handleDishDetailCallback(Update update, Long userId, String data) {
        try {
            // Парсинг ID блюда из строки формата "команда:r:id"
            Long dishId = Long.valueOf(data.split(DELIMITER)[2]);
            Dish dish = dishService.getUserDishOrNull(userId, dishId);
            if (dish == null) {
                // Блюдо уже удалили или id в callback_data чужой
                calorieTelegramBot.sendReturnedMessage(update.getCallbackQuery().getMessage().getChatId(),
                        texts.error(userId, ErrorStatus.DISH_NOT_FOUND));
                return;
            }

            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    dishTexts.buildDishCardText(dish, userId),
                    dishKeyboards.buildPortionKeyboard(dish, userId),
                    null
            );
        } catch (Exception e) {
            log.error("Failed to parse dish ID from callback: {}", data, e);
            calorieTelegramBot.sendReturnedMessage(update.getCallbackQuery().getMessage().getChatId(),
                    texts.get(userId, BotTextKeys.COMMON_ERROR));
        }
    }

    /**
     * Прошлый день — только просмотр: блюда, итоги, шкалы. Сегодня (или дата вне окна листания) — обычный экран «Сегодня».
     */
    private void showDay(Long chatId, Integer messageId, Long userId, LocalDate requested) {
        LocalDate today = getUserToday(userId);
        LocalDate date = getDayInRange(requested, today);
        if (date.equals(today)) {
            calorieTelegramBot.sendEditMessage(chatId, getTodaySummaryText(userId), messageId, getStatsKeyboard(userId));
            return;
        }
        calorieTelegramBot.sendEditMessage(
                chatId,
                dishTexts.buildDayDiaryText(userId, date, getDayTitle(userId, date)),
                messageId,
                buildPastDayKeyboard(userId, date, today)
        );
    }

    /** null или дата из будущего — сегодня; дальше MAX_DAYS_BACK — самый ранний доступный день. */
    static LocalDate getDayInRange(LocalDate requested, LocalDate today) {
        if (requested == null || requested.isAfter(today)) return today;
        LocalDate earliest = today.minusDays(MAX_DAYS_BACK);
        return requested.isBefore(earliest) ? earliest : requested;
    }

    private static LocalDate parseDayOrNull(String data) {
        try {
            return LocalDate.parse(data.substring(DAY_CMD.length()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate getUserToday(Long userId) {
        return Instant.now().atZone(userSettingsService.getZoneId(userId)).toLocalDate();
    }

    /** Заголовок дневника: «12 ОКТЯБРЯ 2026» / «12 OCTOBER 2026». */
    private String getDayTitle(Long userId, LocalDate date) {
        AppLanguage language = texts.languageOf(userId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", language.getLocale());
        return date.format(formatter).toUpperCase(language.getLocale());
    }


    /**
     * Обновляет текущее сообщение, заменяя клавиатуру статистики на список блюд для удаления.
     */
    private void sendRemoveMessage(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getAppUserId();
        List<Dish> dishes = dishService.getTodayDishes(userId);

        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                getTodaySummaryText(userId),
                update.getCallbackQuery().getMessage().getMessageId(),
                buildRemoveKeyboard(dishes, userId)
        );
    }

    /**
     * Отправляет новое сообщение со сводкой за день и клавиатурой статистики.
     */
    public void sendTodayMessage(Long chatId, Long userId) {
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                getTodaySummaryText(userId),
                getStatsKeyboard(userId),
                null
        );
    }

    /**
     * Генерирует текстовый отчет по блюдам и КБЖУ профиля пользователя.
     */
    private String getTodaySummaryText(Long userId) {
        return dishTexts.buildTodayDiaryText(userId, getDayTitle(userId, getUserToday(userId)));
    }

    /**
     * Строит клавиатуру, где каждая кнопка — это название блюда для перехода к действиям над ним.
     */
    private InlineKeyboardMarkup buildRemoveKeyboard(List<Dish> dishes, Long userId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (Dish dish : dishes) {
            builder.row(TelegramKeyboard.button(dish.getName(), GET_DISH_CMD + dish.getId()));
        }
        builder.row(menuButtons.buildBackButton(userId, getHandlerListName()));

        return builder.build();
    }

    /**
     * Строит клавиатуру с визуальными индикаторами (барами) прогресса по калориям, БЖУ и воде.
     */
    /** Сегодня: [ ◀ ] [ Сегодня ] [ · ], шкалы, [ 🗑 Удалить ] [ 📸 Поделиться днём ], «← Меню». */
    public InlineKeyboardMarkup getStatsKeyboard(Long userId) {
        LocalDate today = getUserToday(userId);
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
                .row(buildDayNavRow(userId, today, today));

        // Шкалы без нормы AnalyticsService не возвращает
        analyticsService.buildTodayBars(userId)
                .forEach(bar -> builder.row(TelegramKeyboard.noActionButton(bar)));

        builder.row(
                TelegramKeyboard.button(texts.get(userId, BotTextKeys.TODAY_BUTTON_GO_TO_REMOVE), REMOVE_CMD, ButtonStyle.DANGER),
                // Карточка «мой день» для сторис и чатов (DayCardCalorieUpdateHandler)
                TelegramKeyboard.button(texts.get(userId, BotTextKeys.CARD_BUTTON), Command.CALORIE_DAY_CARD.getCommandText(),
                        ButtonStyle.PRIMARY)
        );
        builder.row(menuButtons.buildBackToMenuButton(userId));

        return builder.build();
    }

    /** Прошлый день: [ ◀ ] [ Сегодня ] [ ▶ ], шкалы за тот день, «← Меню». */
    private InlineKeyboardMarkup buildPastDayKeyboard(Long userId, LocalDate date, LocalDate today) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
                .row(buildDayNavRow(userId, date, today));

        analyticsService.buildDayBars(userId, date)
                .forEach(bar -> builder.row(TelegramKeyboard.noActionButton(bar)));

        builder.row(menuButtons.buildBackToMenuButton(userId));
        return builder.build();
    }

    /**
     * [ ◀ ] [ Сегодня ] [ ▶ ] — одинаково на всех днях. Какой день открыт — в заголовке дневника.
     * На самом раннем дне вместо «◀», на сегодня вместо «▶» — заглушка; накануне сегодня «▶» ведёт на сегодня.
     */
    private List<InlineKeyboardButton> buildDayNavRow(Long userId, LocalDate date, LocalDate today) {
        LocalDate previous = date.minusDays(1);
        InlineKeyboardButton back = previous.isBefore(today.minusDays(MAX_DAYS_BACK))
                ? TelegramKeyboard.placeholderButton()
                : TelegramKeyboard.button("◀", DAY_CMD + previous);

        LocalDate next = date.plusDays(1);
        InlineKeyboardButton forward;
        if (date.equals(today)) {
            forward = TelegramKeyboard.placeholderButton();
        } else if (next.equals(today)) {
            forward = TelegramKeyboard.button("▶", getHandlerListName());
        } else {
            forward = TelegramKeyboard.button("▶", DAY_CMD + next);
        }

        return List.of(
                back,
                TelegramKeyboard.button(texts.get(userId, BotTextKeys.TODAY_BUTTON_TODAY), getHandlerListName()),
                forward
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TODAY_LIST.getCommandText();
    }
}
