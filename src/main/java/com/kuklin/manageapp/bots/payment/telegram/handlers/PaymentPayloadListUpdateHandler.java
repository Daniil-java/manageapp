package com.kuklin.manageapp.bots.payment.telegram.handlers;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.PricingPlan;
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

import java.util.List;

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
    private final PricingPlanService pricingPlanService;
    private static final String PLAN_TEXT =
            """
                    Выберите один из планов:
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        List<PricingPlan> planList = pricingPlanService.getAllPlans();

        paymentTelegramBot.sendReturnedMessage(
                chatId,
                PLAN_TEXT,
                getKeyboardPlan(planList),
                null
        );

    }

    //Возвращает клавиатуру с ТАРИФОМ (в данном случае 10 генераций)
    private InlineKeyboardMarkup getKeyboardPlan(List<PricingPlan> planList) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (PricingPlan plan : planList) {
            builder.row(TelegramKeyboard.button(
                    plan.getTitle(),
                    nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + plan.getId()
            ));
            builder.row(
                    TelegramKeyboard.button(
                            plan.getTitle() + " ОПЛАТА ССЫЛКОЙ",
                            Command.PAYMENT_YOOKASSA_URL_CREATE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + plan.getId()
                    ));
        }
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
