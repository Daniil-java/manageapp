package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Статус подписки: «💎 Сейчас: Premium до 13 ноября · ещё 7 дней» и, если подписок несколько, из чего
 * сложился срок. Приходит после оплаты (SuccessfulPaymentUpdateHandler подменяет апдейт) и по кнопке
 * «Статус подписки» под старыми тарифами — в новых тарифах статус уже в подписи.
 */
@RequiredArgsConstructor
@Component
public class SubscriptionStatusCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final SubscriptionTexts subscriptionTexts;
    private final PremiumOfferButtons premiumOfferButtons;
    private final CalorieMenuButtons menuButtons;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery() ?
                update.getCallbackQuery().getMessage().getChatId() :
                update.getMessage().getChatId();
        Long appUserId = telegramUser.getAppUserId();

        calorieTelegramBot.sendReturnedMessage(chatId, subscriptionTexts.buildStatusDetails(appUserId),
                buildKeyboard(texts.languageOf(appUserId)), null);
    }

    private InlineKeyboardMarkup buildKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(premiumOfferButtons.buildPremiumButton(language))
                .row(menuButtons.buildCloseButton(language))
                .build();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_BALANCE.getCommandText();
    }
}
