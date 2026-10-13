package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.paymentpart;

import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.texts.PlanTexts;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramUserService;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.handlers.PaymentProviderChoiceUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class PaymentPlanListCalorieUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final PaymentProviderChoiceUpdateHandler nextHandler;
    private final CommonPaymentFacade commonPaymentFacade;
    private final CalorieTexts texts;
    private final CalorieMenuButtons menuButtons;
    private final TelegramUserService telegramUserService;
    private final PlanTexts planTexts;

    /** Картинка тарифов на языке пользователя: tarif_ru.png, tarif_en.png. */
    private static final String IMAGE_DIR = "static/images/caloriescreens/price/";
    /** Нет картинки на языке пользователя — показываем русскую. */
    private static final AppLanguage FALLBACK_IMAGE_LANGUAGE = AppLanguage.RU;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        sendSubListMessage(chatId, telegramUser.getAppUserId());
    }

    /**
     * «Premium закончился» — зовёт общий планировщик подписок, у него есть только chatId.
     * В личном чате chatId = Telegram ID, по нему находим пользователя и его язык.
     */
    public void sendSubExpiredAdMessage(Long chatId) {
        TelegramUser telegramUser = telegramUserService
                .getTelegramUserByTelegramIdAndBotIdentifierOrNull(chatId, BotIdentifier.CALORIE_BOT);
        AppLanguage language = texts.languageOf(telegramUser == null ? null : telegramUser.getAppUserId());
        calorieTelegramBot.sendReturnedMessage(
                chatId,
                texts.get(language, BotTextKeys.PAYMENT_PREMIUM_EXPIRED),
                getSubButton(language),
                null
                );
    }

    private InlineKeyboardMarkup getSubButton(AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        builder.row(
                TelegramKeyboard.button(menuButtons.getLabel(CalorieMenuButtons.Button.SUBSCRIPTION, language),
                        Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText())
        );
        builder.row(
                TelegramKeyboard.button("❌" + texts.get(language, BotTextKeys.COMMON_BUTTON_CLOSE),
                        Command.CALORIE_CLOSE.getCommandText())
        );
        return builder.build();
    }

    public void sendSubListMessage(Long chatId, Long appUserId) {
        AppLanguage language = texts.languageOf(appUserId);
        List<PricingPlan> planList = commonPaymentFacade
                .getPricingPlans(calorieTelegramBot.getBotIdentifier());

        byte[] photoBytes = loadImageFromResourcesOrNull(language);
        if (photoBytes == null && language != FALLBACK_IMAGE_LANGUAGE) {
            photoBytes = loadImageFromResourcesOrNull(FALLBACK_IMAGE_LANGUAGE);
        }

        if (photoBytes == null) {
            log.error("Error returning message!");
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(language, BotTextKeys.PAYMENT_IMAGE_ERROR));
            return;
        }

        calorieTelegramBot.sendPhotoMessage(
                chatId,
                photoBytes,
                "tarif.png",
                texts.get(language, BotTextKeys.PAYMENT_PLANS),
                getKeyboardPlan(planList, language)
        );
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

    // Формирует inline-клавиатуру с тарифами:
    // каждая кнопка содержит название плана и callback с его id.
    // Названия тарифов — PlanTexts: словарь plans_*, нет ключа — pricing_plans.title.
    private InlineKeyboardMarkup getKeyboardPlan(List<PricingPlan> planList, AppLanguage language) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (PricingPlan plan : planList) {
            builder.row(TelegramKeyboard.button(
                    planTexts.getTitle(plan, language),
                    nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + plan.getId()
            ));
        }
        builder.row(
                TelegramKeyboard.button(texts.get(language, BotTextKeys.PAYMENT_BUTTON_STATUS),
                        Command.PAYMENT_BALANCE.getCommandText())
        );
        builder.row(
                TelegramKeyboard.button("❌" + texts.get(language, BotTextKeys.COMMON_BUTTON_CLOSE),
                        Command.CALORIE_CLOSE.getCommandText())
        );
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
