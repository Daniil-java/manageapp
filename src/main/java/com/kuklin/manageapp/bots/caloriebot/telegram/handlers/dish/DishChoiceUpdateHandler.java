package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishChoiceChatModelService;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
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
public class DishChoiceUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final DishChoiceChatModelService dishChoiceChatModelService;
    private final CalorieTextsService texts;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        DishChoiceChatModel dishChoice = dishChoiceChatModelService
                .setChoiceTrueOrNull(parseDishChoiceModelIdOrNull(callbackQuery.getData()));

        if (dishChoice == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(telegramUser.getAppUserId(), BotTextKeys.DISH_CHOICE_ERROR));
        } else {
            calorieTelegramBot.sendEditMessageReplyMarkupNull(chatId, messageId);
        }
    }

    public Long parseDishChoiceModelIdOrNull(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        // Проверяем, что структура корректная
        if (parts.length < 2) {
            return null;
        }

        // parts[0] = Command.CALORIE_CHOICE
        return Long.parseLong(parts[1]);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_CHOICE.getCommandText();
    }
}
