package com.kuklin.manageapp.payment.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_failed_log")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PaymentFailedLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long paymentId;
    private String errorMessage;
    @Enumerated(EnumType.STRING)
    private Status status;
    @CreationTimestamp
    private LocalDateTime created;

    public enum Status {
        PROCESSED, PENDING;
    }
}
