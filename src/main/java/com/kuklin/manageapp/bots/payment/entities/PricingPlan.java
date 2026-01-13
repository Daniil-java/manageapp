package com.kuklin.manageapp.bots.payment.entities;

import com.kuklin.manageapp.bots.payment.models.common.Currency;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_plans")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PricingPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private Currency currency;
    private Integer priceMinor;
    @Enumerated(EnumType.STRING)
    private PricingPlanType payloadType;
    private Long generationsCount;
    private Integer durationDays;
    @Enumerated(EnumType.STRING)
    private PlanStatus planStatus;
    private String codeForOrderId;
    @CreationTimestamp
    private LocalDateTime created;

    enum PricingPlanType {
        //План для пакетов с запросами
        //durationDays не используется в этом случае
        GENERATION_REQUEST,
        //План для подписок
        //generationsCount не используется в этом случае
        SUBSCRIPTION
        ;
    }

    enum PlanStatus {
        AVAILABLE, DISABLED;
    }
}
