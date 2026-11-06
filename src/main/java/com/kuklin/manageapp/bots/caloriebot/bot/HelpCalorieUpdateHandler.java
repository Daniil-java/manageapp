package com.kuklin.manageapp.bots.caloriebot.bot;

import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.web.CalorieSiteLinks;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.ButtonStyle;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.stream.Collectors;

/**
 * /help — как записывать еду и список команд. Команды — те же, что в списке «/» (CalorieBotCommandsPublisher),
 * поэтому новая публичная команда появляется в /help сама.
 */
@Component
@RequiredArgsConstructor
public class HelpCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final CalorieMenuButtons menuButtons;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasMessage()) return;
        Long appUserId = telegramUser.getAppUserId();
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                buildHelpText(appUserId),
                buildHelpKeyboard(appUserId),
                null
        );
    }

    String buildHelpText(Long appUserId) {
        String commands = CalorieBotCommandsPublisher.PUBLIC_COMMANDS.stream()
                // Сам /help в своём списке не нужен
                .filter(c -> c.command() != Command.CALORIE_HELP)
                .map(c -> c.command().getCommandText() + " — " + texts.get(appUserId, c.descriptionKey()))
                .collect(Collectors.joining("\n"));
        return texts.get(appUserId, BotTextKeys.HELP_TEXT, commands);
    }

    private InlineKeyboardMarkup buildHelpKeyboard(Long appUserId) {
        return TelegramKeyboard.builder()
                .row(TelegramKeyboard.webAppButton(texts.get(appUserId, BotTextKeys.COMMON_BUTTON_INSTRUCTION),
                        CalorieSiteLinks.INSTRUCTION_URL, ButtonStyle.PRIMARY))
                .row(menuButtons.buildCloseButton(appUserId))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_HELP.getCommandText();
    }
}
