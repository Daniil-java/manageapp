package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@RequiredArgsConstructor
@Component
@Slf4j
public class MenuCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieMenuButtons menuButtons;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        String text = texts.get(telegramUser.getAppUserId(), BotTextKeys.MENU_TEXT);
        if (update.hasMessage()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    text,
                    menuButtons.buildInlineMenu(telegramUser.getAppUserId()),
                    null
            );
        } else if (update.hasCallbackQuery()) {
            MaybeInaccessibleMessage message = update.getCallbackQuery().getMessage();
            Long chatId = message.getChatId();

            if (CalorieMenuButtons.BACK_TO_MENU_DATA.equals(update.getCallbackQuery().getData())) {
                // «← Меню» с экрана раздела — меню на место этого экрана
                if (message instanceof Message withContent && withContent.hasPhoto()) {
                    // Сообщение с картинкой (тарифы) в текстовое не превратить — заменяем новым
                    calorieTelegramBot.sendDeleteMessage(chatId, message.getMessageId());
                    calorieTelegramBot.sendReturnedMessage(chatId, text, menuButtons.buildInlineMenu(telegramUser.getAppUserId()), null);
                } else {
                    calorieTelegramBot.sendEditMessage(chatId, text, message.getMessageId(),
                            menuButtons.buildInlineMenu(telegramUser.getAppUserId()));
                }
                return;
            }

            // Кнопка «Меню» под другим сообщением (старт-флоу): сообщение остаётся как было
            // (с форматированием и ссылками), убираем только кнопки, меню — новым сообщением
            calorieTelegramBot.sendEditMessageReplyMarkupNull(chatId, message.getMessageId());

            calorieTelegramBot.sendReturnedMessage(
                    chatId,
                    text,
                    menuButtons.buildInlineMenu(telegramUser.getAppUserId()),
                    null
            );
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_MENU.getCommandText();
    }
}
