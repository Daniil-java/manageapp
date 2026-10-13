package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.favorites;

import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.components.services.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.FeatureLimitNotifier;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish.DishKeyboards;
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
    private final CalorieTexts texts;
    private final DishKeyboards dishKeyboards;


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;

        CallbackQuery query = update.getCallbackQuery();
        Long chatId = query.getMessage().getChatId();

        Long dishId = extractDishIdOrNull(query.getData());
        if (dishId == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.error(telegramUser.getAppUserId(), ErrorStatus.DISH_NOT_FOUND));
            return;
        }

        AccessResult<UserFavoriteDish> result;
        try {
            result = userFavoriteDishService.saveFromDish(telegramUser.getAppUserId(), dishId);
        } catch (ErrorResponseException e) {
            calorieTelegramBot.sendReturnedMessage(chatId, toUserMessage(telegramUser.getAppUserId(), e));
            return;
        } catch (Exception e) {
            log.error("Favorite dish save failed! appUserId {}, dishId {}", telegramUser.getAppUserId(), dishId, e);
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(telegramUser.getAppUserId(), BotTextKeys.FAVORITES_SAVE_FAILED));
            return;
        }

        // Аспект @RequiresFeature возвращает denied, если лимит DISH_FAVORITE_LIST исчерпан
        if (!result.isAllowed()) {
            featureLimitNotifier.sendLimitReached(chatId, telegramUser.getAppUserId(), result.deniedFeature());
            return;
        }

        calorieTelegramBot.sendReturnedMessage(chatId, texts.get(telegramUser.getAppUserId(), BotTextKeys.FAVORITES_SAVED));

        calorieTelegramBot.sendEditMessage(
                chatId,
                query.getMessage().getText(),                         // текст оставляем как был
                query.getMessage().getMessageId(),
                dishKeyboards.buildFavoritePortionKeyboard(dishService.getDishByIdOrNull(dishId), telegramUser.getAppUserId())  // новая клавиатура
        );
    }

    private String toUserMessage(Long appUserId, ErrorResponseException e) {
        return switch (e.getErrorStatus()) {
            case FAVORITE_DISH_ALREADY_EXISTS, DISH_NOT_FOUND -> texts.error(appUserId, e);
            default -> texts.get(appUserId, BotTextKeys.FAVORITES_SAVE_FAILED);
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
