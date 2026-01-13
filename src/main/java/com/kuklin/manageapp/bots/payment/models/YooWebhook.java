package com.kuklin.manageapp.bots.payment.models;

import lombok.Data;

import java.util.Map;

//Вебхук с ЮКассы
@Data
public class YooWebhook {
    private String type;   // всегда "notification"
    private String event;  // напр. "payment.succeeded", "payment.canceled", "payment.waiting_for_capture", "refund.succeeded"
    private PaymentObject object;

    @Data
    public static class PaymentObject {
        private String id;               // id платежа в ЮKassa
        private String status;           // succeeded | canceled | waiting_for_capture ...
        private Boolean paid;            // true/false
        private Amount amount;           // { "value": "10.00", "currency": "RUB" }
        private String description;      // если есть
        private Map<String, Object> metadata; // если вдруг вы будете прокидывать свои данные
    }

    @Data
    public static class Amount {
        private String value;
        private String currency;
    }
}
