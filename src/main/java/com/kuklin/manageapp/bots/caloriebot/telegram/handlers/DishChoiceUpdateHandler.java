package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.aiassistantcalendar.telegram.AssistantTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.services.DishChoiceChatModelService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.UpdateHandler;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

@RequiredArgsConstructor
@Component
public class DishChoiceUpdateHandler implements UpdateHandler {
    private final AssistantTelegramBot assistantTelegramBot;
    private final DishChoiceChatModelService dishChoiceChatModelService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        DishChoiceChatModel dishChoiceChatModel = parseCallbackToEntityOrNull(callbackQuery.getData());
        if (dishChoiceChatModel == null) {
            assistantTelegramBot.sendReturnedMessage(chatId, "Не получилось сохранить результат!");
        } else {
            dishChoiceChatModelService.createChoice(dishChoiceChatModel);
            assistantTelegramBot.sendEditMessageReplyMarkupNull(chatId, messageId);
        }
    }

    public DishChoiceChatModel parseCallbackToEntityOrNull(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);

        // Проверяем, что структура корректная
        if (parts.length < 7) {
            return null;
        }

        // parts[0] = Command.CALORIE_CHOICE
        Long dishId = Long.parseLong(parts[1]);
        ChatModel chatModel = ChatModel.valueOf(parts[2]);
        Integer calories = Integer.parseInt(parts[4]);
        Integer proteins = Integer.parseInt(parts[5]);
        Integer fats = Integer.parseInt(parts[6]);
        Integer carbohydrates = Integer.parseInt(parts[7]);

        return new DishChoiceChatModel()
                .setDishId(dishId)
                .setChatModel(chatModel)
                .setCalories(calories)
                .setProteins(proteins)
                .setFats(fats)
                .setCarbohydrates(carbohydrates);
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_CHOICE.getCommandText();
    }
}
