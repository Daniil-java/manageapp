package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@RequiredArgsConstructor
@Component
public class DeleteUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishService dishService;
    private final DishKeyboards dishKeyboards;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callback = update.getCallbackQuery();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        Long dishId = extractDishIdOrNull(callback.getData());
        if (dishId == null) return;

        // Чужое или уже удалённое блюдо — кнопку не трогаем
        if (!dishService.removeUserDish(telegramUser.getAppUserId(), dishId)) return;
        calorieTelegramBot.editMarkup(chatId, messageId, dishKeyboards.buildNotCountedKeyboard(telegramUser.getAppUserId()));
    }

    private Long extractDishIdOrNull(String data) {
        try {
            String[] parts = data.split(TelegramBot.DEFAULT_DELIMETER);
            String dishId = parts[1];
            return Long.parseLong(dishId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_DELETE.getCommandText();
    }
}
