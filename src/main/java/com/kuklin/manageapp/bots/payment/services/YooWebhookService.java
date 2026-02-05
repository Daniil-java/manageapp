package com.kuklin.manageapp.bots.payment.services;

import com.kuklin.manageapp.bots.payment.entities.Payment;
import com.kuklin.manageapp.bots.payment.entities.WebhookEvent;
import com.kuklin.manageapp.bots.payment.integrations.YooKassaFeignClient;
import com.kuklin.manageapp.bots.payment.models.YooWebhook;
import com.kuklin.manageapp.bots.payment.repositories.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

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

            //Проверяем, не обрабатывали ли уже событие с таким provider/event/id
            if (webhookEventRepository.isAlreadyProcessed(
                    WebhookEvent.WebhookProvider.YOOKASSA, eventType, objectId)) {
                log.info("Skip duplicate YooKassa event: {} {}", eventType, objectId);
                saved.markProcessed();
                webhookEventRepository.save(saved);
                return;
            }


            // Находим связанный платёж Payment в нашей БД.
            // ВАЖНО: при оплате в Telegram сохраняй providerPaymentChargeId в поле, по которому ты ищешь.
            Payment payment = paymentService.findByProviderPaymentIdIdOrNull(objectId, hook);

            if (payment == null) {
                log.warn("YooKassa webhook: payment not found, id={}", objectId);
                saved.markError("PAYMENT_NOT_FOUND");
                webhookEventRepository.save(saved);
                return;
            }

            //Сверяем сумму/валюту (у ЮKassa в рублях с точкой, у нас в мин. единицах)
            String valueStr = hook.getObject().getAmount().getValue();      // например "199.00"
            String currency = hook.getObject().getAmount().getCurrency();   // "RUB"
            long amountMinor = toMinorUnits(valueStr);                       // 19900

            if (payment.getAmount() == null
                    || payment.getAmount().longValue() != amountMinor
                    || !currency.equals(payment.getCurrency().name())) {
                log.error("Amount/Currency mismatch: hook={} {}, db={} {}, paymentId={}",
                        amountMinor, currency, payment.getAmount(), payment.getCurrency(), payment.getId());
                // решай по бизнесу: пометить как подозрительное, но не падаем
            }

            //Обновляем статус по событию (строки → енамы)
            paymentService.setStatus(payment, eventType, hook, objectId);

            saved.markProcessed();
            webhookEventRepository.save(saved);

        } catch (Exception e) {
            log.error("YooKassa webhook handle error", e);
            saved.markError(e.getClass().getSimpleName() + ": " + e.getMessage());
            webhookEventRepository.save(saved);
            // 200 мы уже отдали — это нормально; ретраи не страшны, у нас идемпотентность
        }
    }

    /**
     * Получение статуса платежа из YooKassa по его идентификатору.
     *
     * Делает запрос GET /v3/payments/{id} через Feign-клиент и маппирует строковый статус
     * из API YooKassa в наш WebhookEventType.
     *
     * @param paymentId идентификатор платежа в YooKassa
     * @return тип события по статусу платежа или null, если платеж не найден/статус неизвестен
     */
    public WebhookEvent.WebhookEventType getPaymentFromYooKassaOrNull(String paymentId) {
        try {
            Map<?, ?> paymentApi = yooKassaFeignClient.getPayment(paymentId);
            if (paymentApi == null) return null;

            String apiStatus = (String) paymentApi.get("status");
            if (apiStatus == null) return null;

            switch (apiStatus) {
                case "succeeded":
                    return WebhookEvent.WebhookEventType.PAYMENT_SUCCEEDED;
                case "canceled":
                    return WebhookEvent.WebhookEventType.PAYMENT_CANCELED;
                case "waiting_for_capture":
                    return WebhookEvent.WebhookEventType.PAYMENT_WAITING_FOR_CAPTURE;
                // ЮKassa ещё даёт pending/authorized/etc. — маппируй при необходимости
                default:
                    return WebhookEvent.WebhookEventType.UNKNOWN;
            }
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
