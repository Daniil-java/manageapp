package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.weight;

import com.kuklin.manageapp.bots.caloriebot.components.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;


@Component
@RequiredArgsConstructor
public class CalorieWeightHistoryUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final AnalyticsService analyticsService;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasCallbackQuery()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getCallbackQuery().getMessage().getChatId(),
                    analyticsService.getWeightHistoryTextList(telegramUser.getTelegramId())
            );
        } else if (update.hasMessage()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    analyticsService.getWeightHistoryTextList(telegramUser.getTelegramId())
            );
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_WEIGHT_HISTORY.getCommandText();
    }
}
