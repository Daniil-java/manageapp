package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.weight;

import com.kuklin.manageapp.bots.caloriebot.texts.WeightTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;


@Component
@RequiredArgsConstructor
public class CalorieWeightHistoryUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final WeightTexts weightTexts;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    weightTexts.buildWeightHistoryText(telegramUser.getAppUserId())
            );
        } else if (update.hasMessage()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    weightTexts.buildWeightHistoryText(telegramUser.getAppUserId())
            );
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEIGHT_HISTORY.getCommandText();
    }
}
