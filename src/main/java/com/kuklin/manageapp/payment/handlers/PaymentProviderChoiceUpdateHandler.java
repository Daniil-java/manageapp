package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.services.exceptions.PricingPlanNotFoundException;
import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
public class PaymentProviderChoiceUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final PaymentPayUpdateHandler nextHandler;
    private final CommonPaymentFacade commonPaymentFacade;
    private static final String PROVIDER_TEXT =
            """
                    Выберите один из способ оплаты:
                    """;
    private static final String PLAN_LIST_ERROR_MSG =
            """
                    Ошибка! Не получилось найти список тарифов!
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        String planId = extractPlanId(update.getCallbackQuery().getData());

        try {
            PricingPlan plan = commonPaymentFacade.getPricingPlanById(Long.valueOf(planId));

            if (plan.getCurrency().equals(Currency.XTR)) {
                update.getCallbackQuery().setData(generateCallBackData(planId, Payment.Provider.STARS.name()));
                nextHandler.handle(update, telegramUser);
            } else {
                telegramBotRegistry.get(telegramUser.getBotIdentifier())
                        .sendEditMessage(
                                chatId,
                                PROVIDER_TEXT,
                                update.getCallbackQuery().getMessage().getMessageId(),
                                getKeyboardProvider(planId)
                        );
            }
        } catch (PricingPlanNotFoundException e) {
            telegramBotRegistry.get(telegramUser.getBotIdentifier())
                    .sendReturnedMessage(chatId, PLAN_LIST_ERROR_MSG);
        }
    }

    private String extractPlanId(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);
        return parts[1];
    }

    private InlineKeyboardMarkup getKeyboardProvider(String planId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (Payment.Provider provider : Payment.Provider.values()) {
            if (provider.equals(Payment.Provider.STARS)) continue;
            builder.row(TelegramKeyboard.button(
                    provider.getTitle(),
                    generateCallBackData(planId, provider.name())
            ));
        }
        return builder.build();
    }

    private String generateCallBackData(String planId, String providerName) {
        return nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER
                + planId + TelegramBot.DEFAULT_DELIMETER
                + providerName;
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PROVIDER.getCommandText();
    }
}
