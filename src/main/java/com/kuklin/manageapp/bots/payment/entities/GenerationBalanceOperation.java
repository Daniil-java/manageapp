package com.kuklin.manageapp.bots.payment.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
