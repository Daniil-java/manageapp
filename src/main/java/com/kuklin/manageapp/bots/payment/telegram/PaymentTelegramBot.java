package com.kuklin.manageapp.bots.payment.telegram;

import com.kuklin.manageapp.bots.payment.configurations.TelegramPaymentBotKeyComponents;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.AsyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class PaymentTelegramBot extends TelegramBot {
    @Autowired
    private PaymentTelegramFacade paymentTelegramFacade;
    @Autowired
    private AsyncService asyncService;

    public PaymentTelegramBot(TelegramPaymentBotKeyComponents components) {
        super(components.getKey());
    }

    @Override
    public void onUpdateReceived(Update update) {
        answerCallback(update);
        paymentTelegramFacade.handleUpdate(update);
//        boolean result = doAsync(asyncService, update, u -> telegramCalorieBotFacade.handleUpdate(update));
//
//        if (!result) {
//            notifyAlreadyInProcess(update);
//        }
    }

    @Override
    public String getBotUsername() {
        return BotIdentifier.PAYMENT.name();
    }

    @Override
    public BotIdentifier getBotIdentifier() {
        return BotIdentifier.PAYMENT;
    }
}
