package com.kuklin.manageapp.bots.caloriebot.favorite.telegram;

import com.kuklin.manageapp.bots.caloriebot.dish.DishService;
import com.kuklin.manageapp.bots.caloriebot.favorite.UserFavoriteDishService;
import com.kuklin.manageapp.bots.caloriebot.favorite.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.web.error.ErrorCode;
import com.kuklin.manageapp.common.web.error.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.access.model.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.access.telegram.FeatureLimitNotifier;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.dish.telegram.DishKeyboards;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.TelegramBot;
import com.kuklin.manageapp.common.telegram.Command;
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
                TelegramBot.getCallbackMessage(query).getText(),                         // текст оставляем как был
                query.getMessage().getMessageId(),
                dishKeyboards.buildFavoritePortionKeyboard(dishService.getUserDishOrNull(telegramUser.getAppUserId(), dishId), telegramUser.getAppUserId())  // новая клавиатура
        );
    }

    private String toUserMessage(Long appUserId, ErrorResponseException e) {
        ErrorCode code = e.getErrorCode();
        return code == ErrorStatus.FAVORITE_DISH_ALREADY_EXISTS || code == ErrorStatus.DISH_NOT_FOUND
                ? texts.error(appUserId, e)
                : texts.get(appUserId, BotTextKeys.FAVORITES_SAVE_FAILED);
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
