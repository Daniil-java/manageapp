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

/**
 * Сервис интеграции с YooKassa.
 *
 * Отвечает за:
 * - подготовку и отправку запросов на создание платежа в YooKassa;
 * - преобразование суммы из младших единиц валюты (копейки/центы) в формат, ожидаемый API YooKassa;
 * - логирование результата создания платежа;
 * - возврат сведений о созданном платеже (id, статус, ссылка на оплату, сырой ответ от YooKassa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YooKassaPaymentService {
    private final YooKassaFeignClient client;

    //Создание сссылки на платеж в ЮКассе
    public Created create(long amountMinor,
                          String currency,
                          String description,
                          Long userId,
                          Long chatId,
                          String productCode,
                          String idempotenceKey) {

        //Формирование запроса в ЮКассу, для получения ссылки на платеж
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

    //Превращение стоимости тарифного плана в объект соответствующий документации ЮКассы
    //В БД количество денег хранится в самой маленькой еденицы валюты (н.п. копейкла или цент)
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
