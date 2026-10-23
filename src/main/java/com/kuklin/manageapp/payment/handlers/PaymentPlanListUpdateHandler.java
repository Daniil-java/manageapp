package com.kuklin.manageapp.payment.handlers;

import com.kuklin.manageapp.payment.components.texts.PaymentText;
import com.kuklin.manageapp.payment.components.texts.PaymentTextResolver;
import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.ButtonStyle;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.payment.components.paymentfacades.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;

/**
 * Обработчик команды Command.PAYMENT_PAYLOAD_PLAN.
 *
 * Отвечает за:
 * - получение актуальных тарифных планов для текущего бота;
 * - отправку сообщения с inline-клавиатурой, где каждая кнопка — отдельный тариф.
 */
@RequiredArgsConstructor
@Component
public class PaymentPlanListUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final PaymentProviderChoiceUpdateHandler nextHandler;
    private final CommonPaymentFacade commonPaymentFacade;
    private final PaymentTextResolver textResolver;

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
                        textResolver.getText(telegramUser.getBotIdentifier(), PaymentText.PLAN_LIST_TITLE,
                                telegramUser.getAppUserId()),
                        getKeyboardPlan(planList, telegramUser.getAppUserId()),
                        null
                );
    }

    // Формирует inline-клавиатуру с тарифами:
    // каждая кнопка содержит название плана и callback с его id.
    private InlineKeyboardMarkup getKeyboardPlan(List<PricingPlan> planList, Long appUserId) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        for (PricingPlan plan : planList) {
            builder.row(TelegramKeyboard.button(
                    textResolver.getPlanTitle(plan, appUserId),
                    nextHandler.getHandlerListName() + TelegramBot.DEFAULT_DELIMETER + plan.getId(),
                    ButtonStyle.SUCCESS
            ));
        }
        return builder.build();
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PAYLOAD_PLAN.getCommandText();
    }
}
