package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import com.kuklin.manageapp.bots.payment.components.integrations.YooKassaFeignClient;
import com.kuklin.manageapp.bots.payment.models.YooWebhook;
import com.kuklin.manageapp.bots.payment.models.yookassa.YookassaPaymentResponse;
import com.kuklin.manageapp.bots.payment.repositories.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис обработки вебхуков YooKassa.
 *
 * Отвечает за:
 * - приём и первичную обработку входящих webhook-событий от YooKassa;
 * - сохранение логов вебхука;
 * - защиту от повторной обработки одного и того же события;
 * - поиск соответствующего Payment в нашей системе и сверку суммы/валюты;
 * - обновление статуса платежа по типу события YooKassa;
 * - при необходимости — запрос актуального статуса платежа через API YooKassa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YooWebhookService {
    private final YooKassaFeignClient yooKassaFeignClient;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;

    /**
     * Обработка входящего вебхука от YooKassa.
     *
     * В случае ошибок помечаем webhook_event как ошибочный и логируем.
     *
     * @param hook       тело вебхука от YooKassa
     * @param remoteAddr IP-адрес источника запроса (для аудита)
     */
    @Transactional
    public void handle(YooWebhook hook, String remoteAddr) {
        log.info("YooWebhookService handle update!");
        // 0) Готовим данные и логируем событие (для идемпотентности и аудита)
        String objectId = hook.getObject() != null ? hook.getObject().getId() : null;

        WebhookEvent.WebhookEventType eventType = mapYooEvent(hook.getEvent());

        log.info("WebhookEvent saving...");
        //Сохраняем событие в таблицу webhook_event
        WebhookEvent saved = webhookEventRepository.save(
                WebhookEvent.incoming(
                        WebhookEvent.WebhookProvider.YOOKASSA,   // было "YOOKASSA"
                        eventType,                                // было hook.getEvent()
                        objectId,
                        remoteAddr,
                        hook
                )
        );
        log.info("WebhookEvent saved!");

        try {

            if (objectId == null) {
                log.warn("YooKassa webhook without object.id: {}", hook);
                saved.markError("NO_OBJECT_ID");
                webhookEventRepository.save(saved);
                return;
            }

            YookassaPaymentResponse response = getPaymentFromYooKassaOrNull(objectId);
            if (response == null) {
                log.warn("YooKassa payment not found by API request: {}", hook);
                saved.markError("NO_PAYMENT_IN_PROVIDER");
                webhookEventRepository.save(saved);
                return;
            }

            // Проверка статуса из API vs тип события
            String apiStatus = response.getStatus();
            if (!isApiStatusCompatible(apiStatus, eventType)) {
                log.error("Status mismatch: eventType={}, apiStatus={}, paymentId={}",
                        eventType, apiStatus, response.getId());
                saved.markError("STATUS_MISMATCH");
                webhookEventRepository.save(saved);
                return;
            }

            //Проверяем, не обрабатывали ли уже событие с таким provider/event/id
            if (webhookEventRepository.isAlreadyProcessed(
                    WebhookEvent.WebhookProvider.YOOKASSA, eventType, response.getId())) {
                log.info("Skip duplicate YooKassa event: {} {}", eventType, response.getId());
                saved.markProcessed();
                webhookEventRepository.save(saved);
                return;
            }


            // Находим связанный платёж Payment в нашей БД.
            // ВАЖНО: при оплате в Telegram сохраняй providerPaymentChargeId в поле, по которому ты ищешь.
            Payment payment = paymentService.findByProviderPaymentId(response.getId(), response);

            if (payment == null) {
                log.warn("YooKassa webhook: payment not found, id={}", response.getId());
                saved.markError("PAYMENT_NOT_FOUND");
                webhookEventRepository.save(saved);
                return;
            }

            //Проверка, что данные о деньгах не null
            if (response.getAmount() == null ||
                    response.getAmount().getValue() == null ||
                    response.getAmount().getCurrency() == null) {
                log.error("YooKassa API: amount/currency is null for id={}", response.getId());
                saved.markError("NO_AMOUNT_IN_PROVIDER");
                webhookEventRepository.save(saved);
                return;
            }

            //Сверяем сумму/валюту (у ЮKassa в рублях с точкой, у нас в мин. единицах)
            String valueStr = response.getAmount().getValue();      // например "199.00"
            String currency = response.getAmount().getCurrency();   // "RUB"
            long amountMinor = toMinorUnits(valueStr);                       // 19900

            //Проверяем, что данные о платежах в нашей базе и в ЮКассе сходятся
            if (payment.getAmount() == null
                    || payment.getAmount().longValue() != amountMinor
                    || !currency.equals(payment.getCurrency().name())) {
                log.error("Amount/Currency mismatch: apiResponse={} {}, db={} {}, paymentId={}",
                        amountMinor, currency, payment.getAmount(), payment.getCurrency(), payment.getId());
                saved.markError("Amount/Currency mismatch");
                webhookEventRepository.save(saved);
                return;
            }

            Payment.PaymentStatus paymentStatus = null;
            //Обновляем статус по событию (строки → енамы)
            switch (eventType) {
                case PAYMENT_CANCELED -> paymentStatus = Payment.PaymentStatus.CANCEL;
                case PAYMENT_SUCCEEDED -> paymentStatus = Payment.PaymentStatus.SUCCESS;
                case REFUND_SUCCEEDED -> paymentStatus = Payment.PaymentStatus.REFUNDED;
            }

            if (payment != null) {
                paymentService.changeStatus(payment, paymentStatus, response.getId());
                saved.markProcessed();
            } else {
                saved.markError("Unknown status");
            }
            webhookEventRepository.save(saved);
        } catch (Exception e) {
            log.error("YooKassa webhook handle error", e);
            saved.markError(e.getClass().getSimpleName() + ": " + e.getMessage());
            webhookEventRepository.save(saved);
            // 200 мы уже отдали — это нормально; ретраи не страшны, у нас идемпотентность
        }
    }

    //Сверка статусов платежа в пришедшем вебхуке и в ответе из запроса в ЮКассу
    private boolean isApiStatusCompatible(String apiStatus, WebhookEvent.WebhookEventType eventType) {
        if (apiStatus == null) {
            return false;
        }

        // Проверяем только платёжные события. Для остальных (REFUND_* и т.п.) — не трогаем.
        switch (eventType) {
            case PAYMENT_SUCCEEDED:
                return "succeeded".equals(apiStatus);

            case PAYMENT_CANCELED:
                return "canceled".equals(apiStatus);

            case PAYMENT_WAITING_FOR_CAPTURE:
                return "waiting_for_capture".equals(apiStatus);

            default:
                // Для REFUND_* и UNKNOWN сейчас просто не проверяем статус,
                // можно расширить позже.
                return true;
        }
    }

    /**
     * Получение актуального состояния платежа из YooKassa по его идентификатору.
     *
     * Делает запрос GET /v3/payments/{id} через Feign-клиент.
     *
     * @param paymentId идентификатор платежа в YooKassa
     * @return полное описание платежа или null, если платеж не найден/произошла ошибка
     */
    public YookassaPaymentResponse getPaymentFromYooKassaOrNull(String paymentId) {
        try {
            return yooKassaFeignClient.getPayment(paymentId);
        } catch (feign.FeignException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.warn("YooKassa getPayment failed for id={}", paymentId, e);
            return null;
        }
    }

    private static WebhookEvent.WebhookEventType mapYooEvent(String event) {
        try {
            return WebhookEvent.WebhookEventType.fromEvent(event);
        } catch (IllegalArgumentException e) {
            return WebhookEvent.WebhookEventType.UNKNOWN;
        }
    }

    private long toMinorUnits(String value) {
        // "199.00" -> 19900; "199.0" -> 19900; "199" -> 19900
        int dot = value.indexOf('.');
        if (dot < 0) return Long.parseLong(value) * 100;
        String major = value.substring(0, dot);
        String minor = value.substring(dot + 1);
        if (minor.length() == 0) minor = "00";
        else if (minor.length() == 1) minor = minor + "0";
        else if (minor.length() > 2) minor = minor.substring(0, 2);
        return Long.parseLong(major) * 100 + Long.parseLong(minor);
    }

}
