package com.kuklin.manageapp.bots.payment.telegram.handlers.telegrampay;

import com.kuklin.manageapp.bots.payment.services.PaymentService;
import com.kuklin.manageapp.bots.payment.telegram.PaymentTelegramBot;
import com.kuklin.manageapp.bots.payment.telegram.handlers.PaymentUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.Command;
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
    private final PaymentTelegramBot paymentTelegramBot;
    private final PaymentService paymentService;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (!update.hasPreCheckoutQuery()) {
            //TODO ERROR
            return;
        }

        PreCheckoutQuery query = update.getPreCheckoutQuery();
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        //Валидация пришедшего запроса.
        //Сравнение с записью из БД
        if (!paymentService.checkPreCheckoutQuery(query)) {
            //TODO ERROR
            //Ответ о неудаче
            answer.setOk(false);
            answer.setErrorMessage("Ошибка данных платежа");
            answer.setPreCheckoutQueryId(query.getId());
            try {
                paymentTelegramBot.execute(answer);
            } catch (TelegramApiException e) {
                //TODO ERROR
            }
            return;
        }

        //Удачный ответ
        answer.setOk(true);
        answer.setPreCheckoutQueryId(query.getId());

        try {
            paymentTelegramBot.execute(answer);
        } catch (TelegramApiException e) {
            //TODO ERROR
        }
    }

    @Override
    public String getHandlerListName() {
        return Command.PAYMENT_PRE_CHECK_QUERY.getCommandText();
    }
}
