package com.kuklin.manageapp.payment.handlers.telegrampay;

import com.kuklin.manageapp.payment.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.payment.CommonPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Обработчик PRE_CHECK_QUERY, который присылает телеграм
 *
 * Отвечает за:
 * -валидацию данных о проходящем платеже
 *
 */
@RequiredArgsConstructor
@Component
public class PreCheckoutQueryUpdateHandler implements PaymentUpdateHandler {
    private final TelegramBotRegistry telegramBotRegistry;
    private final CommonPaymentFacade commonPaymentFacade;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        PreCheckoutQuery query = update.getPreCheckoutQuery();
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        //Валидация пришедшего запроса.
        //Сравнение с записью из БД
        TelegramBot telegramBot = telegramBotRegistry.get(telegramUser.getBotIdentifier());

        if (!commonPaymentFacade.checkPreCheckoutQuery(query)) {
            //Ответ о неудаче
            answer.setOk(false);
            answer.setErrorMessage("Ошибка данных платежа");
            answer.setPreCheckoutQueryId(query.getId());
            try {
                telegramBot.execute(answer);
            } catch (TelegramApiException e) {
                //TODO ERROR
            }
            return;
        }

        //Удачный ответ
        answer.setOk(true);
        answer.setPreCheckoutQueryId(query.getId());

        try {
            telegramBot.execute(answer);
        } catch (TelegramApiException e) {
            //TODO ERROR
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PRE_CHECK_QUERY.getCommandText();
    }
}
