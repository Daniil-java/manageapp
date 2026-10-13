package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.favorites;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.texts.DishTexts;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishKeyboards;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CalorieFavoriteDishUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserFavoriteDishService userFavoriteDishService;
    private final DishTexts dishTexts;
    private final DishKeyboards dishKeyboards;
    private final CalorieTexts texts;
    private static final Integer PAGE_SIZE = 5;

    private static final String ADD_CMD = "ADD";
    private static final String PAGE_CMD = "PAGE";
    private static final String CLOSE_CMD = Command.CALORIE_CLOSE.getCommandText();
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            processMessage(update, telegramUser);
        } else if (update.hasCallbackQuery()){
            processCallback(update, telegramUser);
        }
    }

    private void processMessage(Update update, TelegramUser telegramUser) {
        Long chatId = update.getMessage().getChatId();
        showFavorites(chatId, telegramUser);
    }

    private String buildListTitle(int total, int page, Long appUserId) {
        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);
        return texts.get(appUserId, BotTextKeys.FAVORITES_PAGE_TITLE, String.valueOf(page + 1), String.valueOf(totalPages));
    }

    /**
     * Обрабатываем нажатия на кнопки списка:
     * - PAGE: перелистнуть страницу
     * - ADD: добавить блюдо из избранного в дневник
     */
    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        String data = callback.getData();

        String action = extractCommandOrNull(data);
        if (action == null) {
            showFavorites(callback.getMessage().getChatId(), telegramUser);
            return;
        }

        if (PAGE_CMD.equals(action)) {
            handlePageCommand(telegramUser, callback);
        } else if (ADD_CMD.equals(action)) {
            handleAddCommand(telegramUser, callback);
        }
    }

    private void showFavorites(Long chatId, TelegramUser telegramUser) {
        Long appUserId = telegramUser.getAppUserId();
        List<UserFavoriteDish> favoriteDishes =
                userFavoriteDishService.getAllForUser(appUserId);

        if (favoriteDishes == null || favoriteDishes.isEmpty()) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.FAVORITES_EMPTY_HINT)
            );
            return;
        }

        int page = 0;

        InlineKeyboardMarkup keyboard =
                buildFavoritesKeyboard(favoriteDishes, page, appUserId);

        String text = buildListTitle(favoriteDishes.size(), page, appUserId);

        calorieTelegramBot.sendReturnedMessage(chatId, text, keyboard, null);
    }


    private void handlePageCommand(TelegramUser telegramUser, CallbackQuery query) {
        Long appUserId = telegramUser.getAppUserId();
        // перелистывание
        Integer page = extractPageOrNull(query.getData(), query.getMessage().getChatId(), appUserId);
        if (page == null) return;
        Long chatId = query.getMessage().getChatId();

        List<UserFavoriteDish> favorites =
                userFavoriteDishService.getAllForUser(appUserId);

        if (favorites == null || favorites.isEmpty()) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.FAVORITES_EMPTY),
                    query.getMessage().getMessageId(),
                    null
            );
            return;
        }

        InlineKeyboardMarkup keyboard = buildFavoritesKeyboard(favorites, page, appUserId);
        String text = buildListTitle(favorites.size(), page, appUserId);

        calorieTelegramBot.sendEditMessage(
                chatId,
                text,
                query.getMessage().getMessageId(),
                keyboard
        );
    }

    private void handleAddCommand(TelegramUser telegramUser, CallbackQuery query) {
        Long appUserId = telegramUser.getAppUserId();
        // добавить блюдо из избранного
        String data = query.getData();
        Long chatId = query.getMessage().getChatId();

        Integer page = extractPageOrNull(data, chatId, appUserId);
        Long favoriteId = extractFavoriteIdOrNull(data, chatId, appUserId);
        if (page == null || favoriteId == null) return;

        Dish dish = userFavoriteDishService.addDishFromFavorite(
                appUserId,
                favoriteId
        );

        if (dish == null) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.FAVORITES_ADD_FAILED)
            );
            return;
        }

        // Отправляем отдельное сообщение с добавленным блюдом
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                dishTexts.buildDishCardText(dish, appUserId),
                dishKeyboards.buildPortionKeyboard(dish, appUserId),
                null
        );

        // Обновляем клавиатуру со списком (чтобы не ломалась навигация)
        List<UserFavoriteDish> favorites =
                userFavoriteDishService.getAllForUser(appUserId);

        InlineKeyboardMarkup keyboard = buildFavoritesKeyboard(favorites, page, appUserId);
        String text = buildListTitle(favorites.size(), page, appUserId);

        calorieTelegramBot.sendEditMessage(
                chatId,
                text,
                query.getMessage().getMessageId(),
                keyboard
        );
    }

    /**
     * Клавиатура списка избранных блюд с листалкой.
     */
    private InlineKeyboardMarkup buildFavoritesKeyboard(List<UserFavoriteDish> favorites, int page, Long appUserId) {
        int total = favorites.size();
        if (total == 0) {
            return TelegramKeyboard.builder().build();
        }

        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int fromIndex = page * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        // Кнопки с блюдами
        for (UserFavoriteDish fav : favorites.subList(fromIndex, toIndex)) {
            String callbackData =
                    Command.CALORIE_FAVORITE.getCommandText()
                            + TelegramBot.DEFAULT_DELIMETER + ADD_CMD
                            + TelegramBot.DEFAULT_DELIMETER + page
                            + TelegramBot.DEFAULT_DELIMETER + fav.getId();

            builder.row(
                    TelegramKeyboard.button(fav.getName(), callbackData)
            );
        }

        String base = Command.CALORIE_FAVORITE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + PAGE_CMD
                + TelegramBot.DEFAULT_DELIMETER;
        // Кнопки перелистывания
        if (totalPages > 1) {

            List<InlineKeyboardButton> navButtons = new ArrayList<>();

            // если есть страница слева
            if (page > 0)
                navButtons.add(TelegramKeyboard.button("⬅", base + (page - 1)));


            // если есть страница справа
            if (page < totalPages - 1)
                navButtons.add(TelegramKeyboard.button("➡", base + (page + 1)));

            if (!navButtons.isEmpty())
                builder.row(navButtons.toArray(new InlineKeyboardButton[0]));

        }
        //Кнопка для возврата в режим удаления
        builder.row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.FAVORITES_BUTTON_DELETE_MODE),
                        Command.CALORIE_FAVORITE_DELETE.getCommandText()
                                + TelegramBot.DEFAULT_DELIMETER + PAGE_CMD
                                + TelegramBot.DEFAULT_DELIMETER + page
                ));

        builder.row(
                TelegramKeyboard.button("❌ " + texts.get(appUserId, BotTextKeys.COMMON_BUTTON_CLOSE), CLOSE_CMD)
        );

        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_FAVORITE.getCommandText();
    }

    private String extractCommandOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts[3];
        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractPageOrNull(String data, Long chatId, Long appUserId) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Integer.parseInt(parts[4]);
        } catch (Exception e) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.COMMON_DATA_ERROR)
            );
            return null;
        }
    }

    private Long extractFavoriteIdOrNull(String data, Long chatId, Long appUserId) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Long.parseLong(parts[5]);
        } catch (Exception e) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.COMMON_DATA_ERROR)
            );
            return null;
        }
    }
}
