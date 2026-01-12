package com.kuklin.manageapp.bots.payment.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscription")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long telegramId;
    private Long pricingPlanId;
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Status {
        ACTIVE,
        SCHEDULED,
        EXPIRED,
        CANCELLED
    }
}
