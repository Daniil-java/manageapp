package com.kuklin.manageapp.bots.caloriebot.telegram.privacyterms;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieSiteLinks;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * /privacy — сюда же ведёт кнопка «Политика конфиденциальности» в профиле бота, если в BotFather не задана ссылка.
 * Сам текст — на сайте (calorieprivacy_<язык>.html), в чат только короткое сообщение со ссылкой.
 */
@Component
@RequiredArgsConstructor
public class PrivacyCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        AppLanguage language = texts.languageOf(telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                texts.get(language, BotTextKeys.LEGAL_PRIVACY_TEXT),
                LegalLinkKeyboards.buildLinkKeyboard(
                        texts.get(language, BotTextKeys.LEGAL_BUTTON_OPEN),
                        CalorieSiteLinks.buildPrivacyUrl(language)),
                null
        );
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PRIVACY.getCommandText();
    }
}
