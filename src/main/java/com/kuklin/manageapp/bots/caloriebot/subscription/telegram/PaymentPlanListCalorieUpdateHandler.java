package com.kuklin.manageapp.bots.caloriebot.subscription.telegram;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.telegram.Command;
import com.kuklin.manageapp.common.telegram.keyboard.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;

/**
 * «💳 Подписка»: картинка тарифов, в подписи — статус («Premium до 13 ноября · ещё 7 дней» или бесплатный
 * план) и коротко, что даёт Premium. Тарифы — кнопками с ценой и выгодой, плюс «Бесплатно — позвать друга».
 * Отсюда же — «Premium закончился» (зовёт общий планировщик подписок).
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class PaymentPlanListCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final CalorieTexts texts;
    private final CalorieMenuButtons menuButtons;
    private final TelegramUserService telegramUserService;
    private final SubscriptionTexts subscriptionTexts;
    private final PremiumOfferButtons premiumOfferButtons;

    /** Картинка тарифов на языке пользователя: tarif_ru.png, tarif_en.png. */
    private static final String IMAGE_DIR = "static/images/caloriescreens/price/";
    /** Нет картинки на языке пользователя — показываем русскую. */
    private static final AppLanguage FALLBACK_IMAGE_LANGUAGE = AppLanguage.RU;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        Message plansMessage = sendSubListMessage(chatId, telegramUser.getAppUserId());

        // Из меню: тарифы — сообщение с картинкой, текстовое меню в него не превратить.
        // Тарифы пришли новым сообщением — меню убираем, чтобы экран был один
        if (plansMessage != null && update.hasCallbackQuery()) {
            MaybeInaccessibleMessage pressedIn = update.getCallbackQuery().getMessage();
            if (menuButtons.isMenuKeyboard(pressedIn)) {
                calorieTelegramBot.sendDeleteMessage(chatId, pressedIn.getMessageId());
            }
        }
    }

    /**
     * «Premium закончился» — зовёт общий планировщик подписок, у него есть только chatId.
     * В личном чате chatId = Telegram ID, по нему находим пользователя и его язык.
     * Подписки идут цепочкой: закончилась одна, а следующая (бонус, купленная) уже на очереди — молчим.
     */
    public void sendSubExpiredAdMessage(Long chatId) {
        TelegramUser telegramUser = telegramUserService
                .getTelegramUserByTelegramIdAndBotIdentifierOrNull(chatId, BotIdentifier.CALORIE_BOT);
        Long appUserId = telegramUser == null ? null : telegramUser.getAppUserId();
        if (appUserId != null && !subscriptionTexts.findWorkingSubscriptions(appUserId).isEmpty()) {
            return;
        }
        AppLanguage language = texts.languageOf(appUserId);
        calorieTelegramBot.sendReturnedMessage(chatId, texts.get(language, BotTextKeys.PAYMENT_PREMIUM_EXPIRED),
                buildOfferKeyboard(language), null);
    }

    /** «💎 Premium — от 299 ⭐», «🎁 Бесплатно — позвать друга», «Закрыть». */
    InlineKeyboardMarkup buildOfferKeyboard(AppLanguage language) {
        return TelegramKeyboard.builder()
                .row(premiumOfferButtons.buildPremiumButton(language))
                .row(premiumOfferButtons.buildInviteButton(language))
                .row(menuButtons.buildCloseButton(language))
                .build();
    }

    /** Сообщение с тарифами; null — не ушло (нет картинки, ошибка Telegram). */
    private Message sendSubListMessage(Long chatId, Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);

        byte[] photoBytes = loadImageFromResourcesOrNull(language);
        if (photoBytes == null && language != FALLBACK_IMAGE_LANGUAGE) {
            photoBytes = loadImageFromResourcesOrNull(FALLBACK_IMAGE_LANGUAGE);
        }

        if (photoBytes == null) {
            log.error("Error returning message!");
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(language, BotTextKeys.PAYMENT_IMAGE_ERROR));
            return null;
        }

        // Подпись с разметкой (<b>) — sendPhotoMessage шлёт подпись простым текстом
        return calorieTelegramBot.sendHtmlPhotoMessage(
                chatId,
                photoBytes,
                "tarif.png",
                buildPlansCaption(appUserId),
                buildPlansKeyboard(language)
        );
    }

    String buildPlansCaption(Long appUserId) {
        return texts.get(appUserId, BotTextKeys.PAYMENT_PLANS_CAPTION, subscriptionTexts.buildStatusLine(appUserId));
    }

    private byte[] loadImageFromResourcesOrNull(AppLanguage language) {
        String imagePath = getImagePath(language);
        ClassPathResource resource = new ClassPathResource(imagePath);
        if (!resource.exists()) {
            log.warn("No pricing image for language {}: {}", language, imagePath);
            return null;
        }
        try {
            return StreamUtils.copyToByteArray(resource.getInputStream());
        } catch (IOException e) {
            log.error("Failed to load the pricing image: {}", imagePath, e);
            return null;
        }
    }

    private static String getImagePath(AppLanguage language) {
        return IMAGE_DIR + "tarif_" + language.getCode() + ".png";
    }

    /** Тарифы с ценой и выгодой, «Бесплатно — позвать друга», «← Меню». Статус — в подписи, отдельная кнопка не нужна. */
    private InlineKeyboardMarkup buildPlansKeyboard(AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();
        premiumOfferButtons.buildPlanButtons(language).forEach(builder::row);
        builder.row(premiumOfferButtons.buildInviteButton(language));
        builder.row(menuButtons.buildBackToMenuButton(language));
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
