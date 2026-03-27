package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class MenuCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private static final String MSG = "FAQ - поможет вам разобраться в боте \uD83D\uDE0A";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.hasMessage()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    MSG,
                    getCommandKeyboard(),
                    null
            );
        }
    }

    public static InlineKeyboardMarkup getCommandKeyboard() {

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton settingsBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_SETTINGS.getCommandText())
                .callbackData(Command.CALORIE_SETTINGS.getCommandText())
                .build();

        InlineKeyboardButton profileBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_PROFILE.getCommandText())
                .callbackData(Command.CALORIE_PROFILE.getCommandText())
                .build();

        InlineKeyboardButton waterBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_WATER.getCommandText())
                .callbackData(Command.CALORIE_WATER.getCommandText())
                .build();

        InlineKeyboardButton favoriteBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_FAVORITE.getCommandText())
                .callbackData(Command.CALORIE_FAVORITE.getCommandText())
                .build();

        InlineKeyboardButton todayListBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_TODAY_LIST.getCommandText())
                .callbackData(Command.CALORIE_TODAY_LIST.getCommandText())
                .build();

        InlineKeyboardButton reportBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_REPORT.getCommandText())
                .callbackData(Command.CALORIE_REPORT.getCommandText())
                .build();

        InlineKeyboardButton welcomeBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_WELCOME.getCommandText())
                .callbackData(Command.CALORIE_WELCOME.getCommandText())
                .build();

        InlineKeyboardButton subBtn = InlineKeyboardButton.builder()
                .text(Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText())
                .callbackData(Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText())
                .build();

        List<List<InlineKeyboardButton>> keyboard = List.of(
                List.of(waterBtn, favoriteBtn),
                List.of(settingsBtn, profileBtn),
                List.of(reportBtn, welcomeBtn),
                List.of(todayListBtn),
                List.of(subBtn)
        );

        markup.setKeyboard(keyboard);

        return markup;
    }


    @Override
    public String getHandlerListName() {
        return Command.CALORIE_MENU.getCommandText();
    }
}
