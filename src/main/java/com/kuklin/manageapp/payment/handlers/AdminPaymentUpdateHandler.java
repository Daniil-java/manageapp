package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.entities.UserSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

@Service
@RequiredArgsConstructor
public class AdminPaymentUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private static final Long ADMIN_TG_ID = 425120436L;
    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        return;
    }

    public void sendPaymentMessageToAdmin(Payment payment) {
        telegramBotRegistry.get(BotIdentifier.PAYMENT)
                .sendReturnedMessage(ADMIN_TG_ID, buildAdminMessage(payment));
    }

    public void sendSubsMessageToAdmin(UserSubscription subscription) {
        telegramBotRegistry.get(BotIdentifier.PAYMENT)
                .sendReturnedMessage(ADMIN_TG_ID, buildSubsAdminMessage(subscription));
    }


    private String buildAdminMessage(Payment payment) {
        StringBuilder builder = new StringBuilder();

        builder.append("ID: ").append(payment.getId()).append("\n")
                .append("TG_ID: ").append(payment.getAppUserId()).append("\n")
                .append("STATUS: ").append(payment.getStatus()).append("\n")
                .append("BOT_ID: ").append(payment.getBotIdentifier()).append("\n")
                .append("PROV_PAY_ID: ").append(payment.getProviderPaymentId()).append("\n")
                .append("TG_INVOICE_ID: ").append(payment.getTelegramInvoicePayload()).append("\n")
                .append("CURR: ").append(payment.getCurrency()).append("\n")
                .append("AMOUNT: ").append(payment.getAmount()).append("\n")
                .append("STARS: ").append(payment.getStarsAmount()).append("\n")
                .append("DESC: ").append(payment.getDescription()).append("\n");
        return builder.toString();
    }

    public void sendSubMessageToAdmin(UserSubscription userSubscription) {
    }

    private String buildSubsAdminMessage(UserSubscription sub) {
        StringBuilder builder = new StringBuilder();

        builder.append("ID: ").append(sub.getId()).append("\n")
                .append("TG_ID: ").append(sub.getAppUserId()).append("\n")
                .append("PLAN_ID: ").append(sub.getPricingPlanId()).append("\n");

        return builder.toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.ADMIN_SEND_MSG.getCommandText();
    }
}
