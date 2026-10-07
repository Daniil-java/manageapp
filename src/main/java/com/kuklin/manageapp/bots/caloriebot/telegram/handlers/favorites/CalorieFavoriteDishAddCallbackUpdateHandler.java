package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.favorites;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.FeatureLimitNotifier;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalorieFavoriteDishAddCallbackUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final UserFavoriteDishService userFavoriteDishService;
    private final DishService dishService;
    private final FeatureLimitNotifier featureLimitNotifier;

    private static final String SAVED_MSG = "⭐ Добавил блюдо в список сохранённых.";
    private static final String ALREADY_EXISTS_MSG = "Блюдо с таким названием уже есть в сохранённых.";
    private static final String DISH_NOT_FOUND_MSG = "Не нашёл это блюдо. Возможно, оно было удалено.";
    private static final String ERROR_MSG = "Не получилось сохранить блюдо. Попробуйте ещё раз.";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Long chatId = query.getMessage().getChatId();

        Long dishId = extractDishIdOrNull(query.getData());
        if (dishId == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, DISH_NOT_FOUND_MSG);
            return;
        }

        AccessResult<UserFavoriteDish> result;
        try {
            result = userFavoriteDishService.saveFromDish(telegramUser.getAppUserId(), dishId);
        } catch (ErrorResponseException e) {
            calorieTelegramBot.sendReturnedMessage(chatId, toUserMessage(e.getErrorStatus()));
            return;
        } catch (Exception e) {
            log.error("Favorite dish save failed! appUserId {}, dishId {}", telegramUser.getAppUserId(), dishId, e);
            calorieTelegramBot.sendReturnedMessage(chatId, ERROR_MSG);
            return;
        }

        // Аспект @RequiresFeature возвращает denied, если лимит DISH_FAVORITE_LIST исчерпан
        if (!result.isAllowed()) {
            featureLimitNotifier.sendLimitReached(chatId, result.deniedFeature());
            return;
        }

        calorieTelegramBot.sendReturnedMessage(chatId, SAVED_MSG);

        calorieTelegramBot.sendEditMessage(
                chatId,
                query.getMessage().getText(),                         // текст оставляем как был
                query.getMessage().getMessageId(),
                DishUpdateHandler.getPortionKeyboardFavorite(dishService.getDishByIdOrNull(dishId))  // новая клавиатура
        );
    }

    private String toUserMessage(ErrorStatus status) {
        return switch (status) {
            case FAVORITE_DISH_ALREADY_EXISTS -> ALREADY_EXISTS_MSG;
            case DISH_NOT_FOUND -> DISH_NOT_FOUND_MSG;
            default -> ERROR_MSG;
        };
    }

    private Long extractDishIdOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_FAVORITE_ADD.getCommandText();
    }
}
