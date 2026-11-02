package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.web.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/** /terms — короткое сообщение со ссылкой на соглашение на сайте (calorieterms_<язык>.html). */
@Component
@RequiredArgsConstructor
public class TermsCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                texts.get(language, BotTextKeys.LEGAL_TERMS_TEXT),
                LegalLinkKeyboards.buildLinkKeyboard(
                        texts.get(language, BotTextKeys.LEGAL_BUTTON_OPEN),
                        CalorieSiteLinks.buildTermsUrl(language)),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_TERMS.getCommandText();
    }
}
