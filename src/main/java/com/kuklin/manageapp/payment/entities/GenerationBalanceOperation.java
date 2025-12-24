package com.kuklin.manageapp.payment.entities;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Лог операции над балансом генераций пользователя.
 *
 * Каждая запись описывает одну операцию:
 * - тип (CREDIT/DEBIT/REFUND),
 * - источник (PAYMENT/GENERATION/MANUAL),
 * - связь с paymentId,
 * - сколько запросов списали/начислили.
 */
@Entity
@Table(name = "generation_balance_operations")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class GenerationBalanceOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long telegramId;

    @Enumerated(EnumType.STRING)
    private OperationType type;
    // CREDIT (пополнение), DEBIT (списание), REFUND (возврат)

    @Enumerated(EnumType.STRING)
    private OperationSource source;
    // PAYMENT, GENERATION и т.п.

    // Внешний идентификатор - к какому событию привязано
    private Long paymentId;    // при пополнении
//    private Long requestId;    // для запросов

    private String comment;
    private Long requestCount;
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum OperationType {
        CREDIT, DEBIT, REFUND
    }

    public enum OperationSource {
        PAYMENT,
        GENERATION,
        MANUAL
    }
}
