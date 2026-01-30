package com.kuklin.manageapp.bots.caloriebot.telegram.history;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.components.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserNutritionProfileService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.stream.Stream;

import static com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishUpdateHandler.getPortionWeightKeyboard;

/**
 * Обработчик для отображения статистики и списка блюд пользователя за текущий день.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodayUpdateHandler implements CalorieBotUpdateHandler {

    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final AnalyticsService analyticsService;
    private final UserNutritionProfileService userNutritionProfileService;

    // Константы для формирования callback-данных
    private static final String DELIMITER = TelegramBot.DEFAULT_DELIMETER;
    private static final String REMOVE_CMD = Command.CALORIE_TODAY_LIST.getCommandText() + DELIMITER + "r";
    private static final String GET_DISH_CMD = REMOVE_CMD + DELIMITER;
    private static final String CLOSE_CMD = Command.CALORIE_CLOSE.getCommandText();

    // Текстовые константы для кнопок и сообщений
    private static final String BTN_BACK = "Назад";
    private static final String BTN_CLOSE = "Закрыть";
    private static final String BTN_GO_TO_REMOVE = "Перейти к удалению";
    private static final String ERROR_MSG = "ОШИБКА";
    private static final String DUMMY_CALLBACK = "temp";

    /**
     * Основная точка входа для обработки обновлений.
     */
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            handleCallback(update, telegramUser);
        } else {
            sendTodayMessage(telegramUser.getTelegramId());
        }
    }

    /**
     * Логика обработки нажатий на инлайн-кнопки.
     */
    private void handleCallback(Update update, TelegramUser telegramUser) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long userId = telegramUser.getTelegramId();

        // Если нажата кнопка возврата к списку/обновления
        if (data.equals(getHandlerListName())) {
            calorieTelegramBot.sendEditMessage(chatId, getTodaySummaryText(userId), messageId, getStatsKeyboard(userId));
        }
        // Если выбрано конкретное блюдо из списка на удаление
        else if (data.startsWith(GET_DISH_CMD)) {
            handleDishDetailCallback(update, userId, data);
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
            Dish dish = dishService.getDishByIdOrNull(dishId);

            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    analyticsService.getInfo(dish, userId),
                    getPortionWeightKeyboard(dish),
                    null
            );
        } catch (Exception e) {
            log.error("Ошибка парсинга ID блюда из callback: {}", data, e);
            calorieTelegramBot.sendReturnedMessage(update.getCallbackQuery().getMessage().getChatId(), ERROR_MSG);
        }
    }

    /**
     * Обновляет текущее сообщение, заменяя клавиатуру статистики на список блюд для удаления.
     */
    private void sendRemoveMessage(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getTelegramId();
        List<Dish> dishes = dishService.getTodayDishes(userId);

        calorieTelegramBot.sendEditMessage(
                update.getCallbackQuery().getMessage().getChatId(),
                getTodaySummaryText(userId),
                update.getCallbackQuery().getMessage().getMessageId(),
                buildRemoveKeyboard(dishes)
        );
    }

    /**
     * Отправляет новое сообщение со сводкой за день и клавиатурой статистики.
     */
    public void sendTodayMessage(Long userId) {
        calorieTelegramBot.sendReturnedMessage(
                userId,
                getTodaySummaryText(userId),
                getStatsKeyboard(userId),
                null
        );
    }

    /**
     * Генерирует текстовый отчет по блюдам и КБЖУ профиля пользователя.
     */
    private String getTodaySummaryText(Long userId) {
        List<Dish> dishes = dishService.getTodayDishes(userId);
        UserNutritionProfile profile = userNutritionProfileService.getOrCreateProfile(userId);
        return Dish.getDishesString(dishes, profile);
    }

    /**
     * Строит клавиатуру, где каждая кнопка — это название блюда для перехода к действиям над ним.
     */
    private InlineKeyboardMarkup buildRemoveKeyboard(List<Dish> dishes) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (Dish dish : dishes) {
            builder.row(TelegramKeyboard.button(dish.getName(), GET_DISH_CMD + dish.getId()));
        }
        builder.row(TelegramKeyboard.button(BTN_BACK, getHandlerListName()));

        return builder.build();
    }

    /**
     * Строит клавиатуру с визуальными индикаторами (барами) прогресса по калориям, БЖУ и воде.
     */
    public InlineKeyboardMarkup getStatsKeyboard(Long userId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        // Собираем все бары аналитики и добавляем только те, которые содержат данные
        Stream.of(
                        analyticsService.getCaloriesBarButtonOrEmpty(userId),
                        analyticsService.getProteinsBarButtonOrEmpty(userId),
                        analyticsService.getFatsBarButtonOrEmpty(userId),
                        analyticsService.getCarbsBarButton(userId),
                        analyticsService.getWaterBarButton(userId)
                ).filter(bar -> bar != null && !bar.isBlank())
                .forEach(bar -> builder.row(TelegramKeyboard.button(bar, DUMMY_CALLBACK)));

        builder.row(TelegramKeyboard.button(BTN_GO_TO_REMOVE, REMOVE_CMD));
        builder.row(TelegramKeyboard.button(BTN_CLOSE, CLOSE_CMD));

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TODAY_LIST.getCommandText();
    }
}