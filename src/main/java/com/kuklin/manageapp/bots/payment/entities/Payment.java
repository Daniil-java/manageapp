package com.kuklin.manageapp.bots.payment.entities;

import com.kuklin.manageapp.bots.payment.models.common.Currency;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long telegramId;
    @Enumerated(EnumType.STRING)
    private Provider provider; // "STARS" | "YOOKASSA"
    private String providerPaymentId; // id платежа в ЮKassa
    private Long pricingPlanId;
    private String telegramInvoicePayload;
    private String description;
    @Enumerated(EnumType.STRING)
    private Currency currency;
    //В саммой маленькой еденице валюты. Копейки, центы
    private Integer amount;
    private Integer starsAmount;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    @Enumerated(EnumType.STRING)
    private ProviderStatus providerStatus;
    @CreationTimestamp
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;

    public enum ProviderStatus {
        NEW,                    // только создали у себя
        WAITING_FOR_CAPTURE,    // двухстадийная схема — удержано, ждём capture
        SUCCEEDED,              // оплачено у провайдера
        CANCELED,               // отменено у провайдера
        REFUND_SUCCEEDED        // успешно возвращено
    }

    public enum Provider {
        YOOKASSA, STARS;
    }

    public enum PaymentStatus {
        CREATED, SUCCESS, FAILED, REFUNDED;
    }
}
