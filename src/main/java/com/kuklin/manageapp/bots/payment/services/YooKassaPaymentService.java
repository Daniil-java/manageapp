package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.integrations.YooKassaFeignClient;
import com.kuklin.manageapp.bots.payment.models.yookassa.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class YooKassaPaymentService {
    private final YooKassaFeignClient client;
    private final YooWebhookService yooWebhookService;

    public Created create(long amountMinor,
                          String currency,
                          String description,
                          Long userId,
                          Long chatId,
                          String productCode,
                          String idempotenceKey) {

        YookassaCreatePaymentRequest req = new YookassaCreatePaymentRequest()
                .setCapture(true)
                .setAmount(new Amount(toDecimal(amountMinor), currency))
                .setDescription(description)
                .setConfirmation(new Confirmation()
                        .setType("redirect")
                        .setReturnUrl("https://kuklin.dev"))
                .setMetadata(new Metadata()
                        .setUserId(String.valueOf(userId))
                        .setTelegramChatId(chatId)
                        .setProductCode(productCode));

        YookassaPaymentResponse payment = client.createPayment(req, idempotenceKey);
        String url = payment.getConfirmation() != null ? payment.getConfirmation().getConfirmationUrl() : null;

        log.info("YK created id={} status={} url={}", payment.getId(), payment.getStatus(), url);
        return new Created(payment.getId(), payment.getStatus(), url, payment);
    }

    private static String toDecimal(long minor) {
        return BigDecimal.valueOf(minor)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    @Getter
    @AllArgsConstructor
    public static class Created {
        private final String id;
        private final String status;
        private final String confirmationUrl;
        private final YookassaPaymentResponse raw;
    }

}
