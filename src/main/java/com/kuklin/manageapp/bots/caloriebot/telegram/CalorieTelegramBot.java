package com.kuklin.manageapp.bots.caloriebot.telegram;

import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class CalorieTelegramBot extends TelegramBot {

    @Autowired
    private TelegramCalorieBotFacade telegramCalorieBotFacade;

    public CalorieTelegramBot(TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents) {
        super(telegramCaloriesBotKeyComponents.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        telegramCalorieBotFacade.handleUpdate(update);
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.CALORIE_BOT.name();
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.CALORIE_BOT;
    }
}

