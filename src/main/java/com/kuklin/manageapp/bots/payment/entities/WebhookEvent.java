package com.kuklin.manageapp.bots.payment.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.payment.models.YooWebhook;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Accessors(chain = true)
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private WebhookProvider provider;     // "YOOKASSA"
    @Enumerated(EnumType.STRING)
    private WebhookEventType event;        // payment.succeeded ...
    private String objectId;     // id платежа/рефанда
    private String remoteAddr;   // IP отправителя
    private boolean processed;
    private String error;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;      // сырой JSON

    public enum WebhookProvider { YOOKASSA, TELEGRAM }
    @Getter
    @RequiredArgsConstructor
    public enum WebhookEventType {
        PAYMENT_WAITING_FOR_CAPTURE("payment.waiting_for_capture"),
        PAYMENT_SUCCEEDED("payment.succeeded"),
        PAYMENT_CANCELED("payment.canceled"),
        REFUND_SUCCEEDED("refund.succeeded"),
        UNKNOWN("unknown");

        private final String eventType;
        private static final java.util.Map<String, WebhookEventType> BY_EVENT = new java.util.HashMap<>();
        static {
            for (WebhookEventType t : WebhookEventType.values()) {
                BY_EVENT.put(t.eventType, t);
            }
        }

        public static WebhookEventType fromEvent(String event) {
            if (event == null) return UNKNOWN;
            WebhookEventType t = BY_EVENT.get(event);
            return t != null ? t : UNKNOWN;
        }
    }
    public static WebhookEvent incoming(WebhookProvider provider,
                                        WebhookEventType event,
                                        String objectId,
                                        String remoteAddr,
                                        Object rawBodyJson) {
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rawBodyJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            json = "{\"marshal_error\":\"" + e.getMessage() + "\"}";
        }
        return new WebhookEvent()
                .setProvider(provider)
                .setEvent(event)
                .setObjectId(objectId)
                .setRemoteAddr(remoteAddr)
                .setPayload(json)
                .setReceivedAt(LocalDateTime.now())
                .setProcessed(false);
    }

    public void markProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
        this.error = null;
    }

    public void markError(String error) {
        this.processed = false;
        this.error = error;
    }
}
