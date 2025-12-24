package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
import com.kuklin.manageapp.bots.payment.models.common.Currency;
import com.kuklin.manageapp.bots.payment.services.PricingPlanService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Component
@RequiredArgsConstructor
public class PaymentProviderChoiceUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentPayUpdateHandler nextHandler;
    private final PricingPlanService pricingPlanService;
    private static final String PROVIDER_TEXT =
            """
                    Выберите один из способ оплаты:
                    """;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        if (!update.hasCallbackQuery()) {}//TODO ERROR

        String planId = extractPlanId(update.getCallbackQuery().getData());
        PricingPlan plan = pricingPlanService
                .getPricingPlanByIdOrNull(Long.valueOf(planId));

        if (plan.getCurrency().equals(Currency.XTR)) {
            update.getCallbackQuery().setData(generateCallBackData(planId, Payment.Provider.STARS.name()));
            nextHandler.handle(update, telegramUser);
        } else {
            paymentTelegramBot.sendEditMessage(
                    chatId,
                    PROVIDER_TEXT,
                    update.getCallbackQuery().getMessage().getMessageId(),
                    getKeyboardProvider(planId)
            );
        }
    }

    private String extractPlanId(String callbackData) {
        String[] parts = callbackData.split(TelegramBot.DEFAULT_DELIMETER);
        return parts[1];
    }

    private InlineKeyboardMarkup getKeyboardProvider(String planId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (Payment.Provider provider: Payment.Provider.values()) {
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
