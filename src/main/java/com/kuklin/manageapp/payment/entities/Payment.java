package com.kuklin.manageapp.payment.entities;

import com.kuklin.manageapp.payment.models.common.Currency;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Запись о платеже внутри системы.
 *
 * Отражает:
 * - пользователя (telegramId),
 * - провайдера и его идентификатор (provider, providerPaymentId),
 * - привязанный тарифный план (pricingPlanId),
 * - сумму в минимальных единицах валюты (amount) и количество звёзд (starsAmount),
 * - текущий статус платежа в нашей системе и у провайдера,
 * - BotIdentifier, к которому относится платёж,
 * - временные метки создания/оплаты/отмены.
 *
 * Для Telegram-звёзд:
 * - currency = XTR, amount = 0, starsAmount > 0;
 * Для обычных валют:
 * - currency = RUB/..., amount > 0, starsAmount = 0.
 */
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
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;
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

    @Getter
    @RequiredArgsConstructor
    public enum Provider {
        YOOKASSA("ЮКасса","yookassa_payment", PaymentFlow.TELEGRAM_INVOICE),
        YOOKASSA_URL("Юкасса [ссылка]", "yookassa_payment", PaymentFlow.PROVIDER_REDIRECT),
        STARS("Звезды", "stars_purchase", PaymentFlow.TELEGRAM_INVOICE);
        private final String title;
        private final String telegramStartParameter;
        private final PaymentFlow providerFlow;
    }

    public enum PaymentFlow {
        TELEGRAM_INVOICE,   // платёж идёт через Telegram-инвойс
        PROVIDER_REDIRECT   // платёж идёт через внешнюю ссылку (ЮKassa и т.п.)
    }

    public enum PaymentStatus {
        CREATED, SUCCESS, FAILED, REFUNDED, CANCEL;
    }
}
