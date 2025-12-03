package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.payment.entities.PricingPlan;
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

import java.util.List;

/**
 * Обработчик комманды Command.PAYMENT_PAYLOAD_PLAN
 * <p>
 * Отвечает за:
 * - возвращение сообщение с клавиатурой-выбором покупки;
 */
@RequiredArgsConstructor
@Component
public class PaymentPlanListUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final PaymentProviderChoiceUpdateHandler nextHandler;
    private final CommonPaymentFacade commonPaymentFacade;
    private static final String PLAN_TEXT =
            """
                    Выберите один из планов:
                    """;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasCallbackQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        List<PricingPlan> planList = commonPaymentFacade
                .getPricingPlans(telegramUser.getBotIdentifier());

        telegramBotRegistry.get(telegramUser.getBotIdentifier())
                .sendReturnedMessage(
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
        }
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
