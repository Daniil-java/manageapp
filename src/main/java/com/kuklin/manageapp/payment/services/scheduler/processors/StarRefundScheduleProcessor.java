package com.kuklin.manageapp.payment.services.scheduler.processors;

import com.kuklin.manageapp.common.components.TelegramBotRegistry;
import com.kuklin.manageapp.common.library.ScheduleProcessor;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.payment.entities.Payment;
import com.kuklin.manageapp.payment.models.StarTransactionsClient;
import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.payment.repositories.PaymentRepository;
import com.kuklin.manageapp.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Сверка звёзд с Telegram: платёж, по которому звёзды ушли обратно, а у нас он ещё SUCCESS, —
 * отменяем покупку. Подстраховка к refunded_payment: апдейт мог потеряться (рестарт, перехват не встал).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StarRefundScheduleProcessor implements ScheduleProcessor {
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final TelegramBotRegistry telegramBotRegistry;

    @Override
    public void process() {
        for (BotIdentifier botIdentifier : paymentRepository
                .findBotIdentifiersByCurrencyAndStatus(Currency.XTR, Payment.PaymentStatus.SUCCESS)) {
            try {
                processBot(botIdentifier);
            } catch (Exception e) {
                log.error("Star refund reconciliation failed for {}", botIdentifier, e);
            }
        }
    }

    private void processBot(BotIdentifier botIdentifier) throws Exception {
        Set<String> refundedChargeIds = buildClient(telegramBotRegistry.get(botIdentifier).getToken())
                .findRefundedChargeIds();
        int cancelled = 0;
        for (String chargeId : refundedChargeIds) {
            if (paymentService.cancelRefundedTelegramPayment(chargeId, null)) cancelled++;
        }
        log.info("Star refund reconciliation {}: {} refunds at Telegram, {} purchases cancelled now",
                botIdentifier, refundedChargeIds.size(), cancelled);
    }

    StarTransactionsClient buildClient(String botToken) {
        return new StarTransactionsClient(botToken);
    }

    @Override
    public String getSchedulerName() { return getClass().getSimpleName(); }
}
