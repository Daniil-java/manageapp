package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Обработчик комманды Command.PAYMENT_PAYLOAD_PLAN
 *
 * Отвечает за:
 * - возвращение сообщение с клавиатурой-выбором покупки;
 */
@RequiredArgsConstructor
@Component
public class PaymentPayloadListUpdateHandler implements PaymentUpdateHandler {
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentPlanUpdateHandler nextHandler;
    private static final String PLAN_TEXT =
            """
                    Выберите один из планов:
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        paymentTelegramBot.sendReturnedMessage(
                chatId,
                PLAN_TEXT,
                getKeyboardPlan(),
                null
        );

    }

    //Возвращает клавиатуру с ТАРИФОМ (в данном случае 10 генераций)
    private InlineKeyboardMarkup getKeyboardPlan() {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (Payment.PaymentPayload payload : Payment.PaymentPayload.values()) {
            builder.row(TelegramKeyboard.button(
                    payload.getDescription(),
                    nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + payload.name()
            ));
            builder.row(
                    TelegramKeyboard.button(
                            payload.getDescription() + " ОПЛАТА ССЫЛКОЙ",
                            Command.PAYMENT_YOOKASSA_URL_CREATE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + payload.name()
                    ));
        }
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
