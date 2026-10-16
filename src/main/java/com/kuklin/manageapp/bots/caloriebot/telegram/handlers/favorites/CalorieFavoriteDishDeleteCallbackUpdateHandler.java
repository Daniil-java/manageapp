package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.favorites;

import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
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
public class CalorieFavoriteDishDeleteCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserFavoriteDishService userFavoriteDishService;
    private final CalorieTexts texts;
    private static final Integer PAGE_SIZE = 5;

    private static final String DEL_CMD = "DEL";
    private static final String PAGE_CMD = "PAGE";
    private static final String CONFIRM_CMD = "CONFIRM";
    private static final String CLOSE_CMD = Command.CALORIE_CLOSE.getCommandText();

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            processCallback(update, telegramUser);
        }
    }

    private String buildListTitle(int total, int page, Long appUserId) {
        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);
        return texts.get(appUserId, BotTextKeys.FAVORITES_DELETE_PAGE_TITLE, String.valueOf(page + 1), String.valueOf(totalPages));
    }

    /**
     * Обрабатываем нажатия на кнопки списка:
     * - PAGE: перелистнуть страницу
     * - ADD: добавить блюдо из избранного в дневник
     */
    private void processCallback(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        String data = callback.getData();

        String action = extractCommandOrNull(data, callback.getMessage().getChatId(), telegramUser.getAppUserId());
        if (action == null) return;

        if (PAGE_CMD.equals(action)) {
            handlePageCommand(telegramUser, callback);
        } else if (DEL_CMD.equals(action)) {
            handleDeleteCommand(telegramUser, callback);
        } else if (CONFIRM_CMD.equals(action)) {
            handleConfirmCommand(telegramUser, callback);
        }
    }

    private void handlePageCommand(TelegramUser telegramUser, CallbackQuery query) {
        // перелистывание
        Integer page = extractPageOrNull(query.getData(), query.getMessage().getChatId(), telegramUser.getAppUserId());
        if (page == null) return;
        Long chatId = query.getMessage().getChatId();

        List<UserFavoriteDish> favorites =
                userFavoriteDishService.getAllForUser(telegramUser.getAppUserId());

        if (favorites == null || favorites.isEmpty()) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    texts.get(telegramUser.getAppUserId(), BotTextKeys.FAVORITES_EMPTY),
                    query.getMessage().getMessageId(),
                    null
            );
            return;
        }

        InlineKeyboardMarkup keyboard = buildFavoritesKeyboard(favorites, page, telegramUser.getAppUserId());
        String text = buildListTitle(favorites.size(), page, telegramUser.getAppUserId());

        calorieTelegramBot.sendEditMessage(
                chatId,
                text,
                query.getMessage().getMessageId(),
                keyboard
        );
    }

    private void handleConfirmCommand(TelegramUser telegramUser, CallbackQuery query) {
        Long chatId = query.getMessage().getChatId();

        Integer page = extractPageOrNull(query.getData(), chatId, telegramUser.getAppUserId());
        Long favoriteId = extractFavoriteIdOrNull(query.getData(), chatId, telegramUser.getAppUserId());
        if (page == null || favoriteId == null) return;

        UserFavoriteDish userFavoriteDish =
                userFavoriteDishService.getUserFavoriteDishOrNull(telegramUser.getAppUserId(), favoriteId);
        if (userFavoriteDish == null) {
            calorieTelegramBot.sendReturnedMessage(chatId,
                    texts.error(telegramUser.getAppUserId(), ErrorStatus.FAVORITE_DISH_NOT_FOUND));
            return;
        }
        calorieTelegramBot.sendEditMessage(
                chatId,
                buildConfirmMessage(userFavoriteDish, telegramUser.getAppUserId()),
                query.getMessage().getMessageId(),
                buildConfirmKeyboard(page, favoriteId, telegramUser.getAppUserId())
        );
    }

    private String buildConfirmMessage(UserFavoriteDish userFavoriteDish, Long appUserId) {
        StringBuilder sb = new StringBuilder();

        sb.append(texts.get(appUserId, BotTextKeys.FAVORITES_DELETE_CONFIRM)).append("\n");
        String emoji = userFavoriteDish.getEmojiIcon() == null ? "" : userFavoriteDish.getEmojiIcon();
        sb.append(emoji).append(userFavoriteDish.getName());
        return sb.toString();
    }

    private InlineKeyboardMarkup buildConfirmKeyboard(int page, Long favoriteId, Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        // Да — реально удалить
        return builder.row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.COMMON_BUTTON_YES),
                        Command.CALORIE_FAVORITE_DELETE.getCommandText()
                                + TelegramBot.DEFAULT_DELIMETER + DEL_CMD
                                + TelegramBot.DEFAULT_DELIMETER + page
                                + TelegramBot.DEFAULT_DELIMETER + favoriteId
                ),
                TelegramKeyboard.button(
                        "❌ " + texts.get(appUserId, BotTextKeys.COMMON_BUTTON_NO),
                        Command.CALORIE_FAVORITE_DELETE.getCommandText()
                                + TelegramBot.DEFAULT_DELIMETER + PAGE_CMD
                                + TelegramBot.DEFAULT_DELIMETER + page
                )
        ).build();
    }

    private void handleDeleteCommand(TelegramUser telegramUser, CallbackQuery query) {
        // добавить блюдо из избранного
        String data = query.getData();
        Long chatId = query.getMessage().getChatId();

        Integer page = extractPageOrNull(data, chatId, telegramUser.getAppUserId());
        Long favoriteId = extractFavoriteIdOrNull(data, chatId, telegramUser.getAppUserId());
        if (page == null || favoriteId == null) return;

        userFavoriteDishService.deleteFavorite(
                favoriteId,
                telegramUser.getAppUserId()
        );

        // Обновляем клавиатуру со списком (чтобы не ломалась навигация)
        List<UserFavoriteDish> favorites =
                userFavoriteDishService.getAllForUser(telegramUser.getAppUserId());

        if (favorites == null || favorites.isEmpty()) {
            calorieTelegramBot.sendEditMessage(
                    chatId,
                    texts.get(telegramUser.getAppUserId(), BotTextKeys.FAVORITES_EMPTY),
                    query.getMessage().getMessageId(),
                    null
            );
            return;
        }

        InlineKeyboardMarkup keyboard = buildFavoritesKeyboard(favorites, page, telegramUser.getAppUserId());
        String text = buildListTitle(favorites.size(), page, telegramUser.getAppUserId());

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
                    Command.CALORIE_FAVORITE_DELETE.getCommandText()
                            + TelegramBot.DEFAULT_DELIMETER + CONFIRM_CMD
                            + TelegramBot.DEFAULT_DELIMETER + page
                            + TelegramBot.DEFAULT_DELIMETER + fav.getId();

            builder.row(
                    TelegramKeyboard.button(fav.getName(), callbackData)
            );
        }

        String base = Command.CALORIE_FAVORITE_DELETE.getCommandText()
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

        //Кнопка для возврата в режим выбора
        builder.row(
                TelegramKeyboard.button(
                        texts.get(appUserId, BotTextKeys.FAVORITES_BUTTON_BACK_TO_ADD),
                        Command.CALORIE_FAVORITE.getCommandText()
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
        return Command.CALORIE_FAVORITE_DELETE.getCommandText();
    }

    private String extractCommandOrNull(String data, Long chatId, Long appUserId) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return parts[1];
        } catch (Exception e) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.COMMON_DATA_ERROR)
            );
            return null;
        }
    }

    private Integer extractPageOrNull(String data, Long chatId, Long appUserId) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Integer.parseInt(parts[2]);
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
            return Long.parseLong(parts[3]);
        } catch (Exception e) {
            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    texts.get(appUserId, BotTextKeys.COMMON_DATA_ERROR)
            );
            return null;
        }
    }
}


