package com.kuklin.manageapp.bots.caloriebot.onboarding.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * «Далее» под приветствием /start. Новое приветствие этой кнопки не содержит (et-120) — она осталась под
 * старыми сообщениями в чатах. Отвечает тем же знакомством, что /intro.
 */
@Component
@RequiredArgsConstructor
public class StartFlowCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final IntroCalorieUpdateHandler introCalorieUpdateHandler;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasCallbackQuery()) return;
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        // Приветствие (со ссылками на политику и соглашение) остаётся, убираем только «Далее» / «Закрыть»
        calorieTelegramBot.sendEditMessageReplyMarkupNull(chatId, update.getCallbackQuery().getMessage().getMessageId());
        introCalorieUpdateHandler.sendIntro(chatId, telegramUser.getAppUserId());
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_START_FLOW_1.getCommandText();
    }
}
