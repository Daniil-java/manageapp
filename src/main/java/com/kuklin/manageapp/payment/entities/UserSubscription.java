package com.kuklin.manageapp.payment.entities;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Подписка пользователя на тарифный план.
 *
 * Статус:
 * - ACTIVE — действует прямо сейчас;
 * - SCHEDULED — начнётся в будущем;
 * - EXPIRED — истекла;
 * - CANCELLED — досрочно отменена.
 *
 * Привязана к:
 * - тарифу (pricingPlanId),
 * - платежу (paymentId),
 * - конкретному боту (botIdentifier).
 */
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
    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;
    private Instant startAt;
    private Instant endAt;
    @CreationTimestamp
    private Instant createdAt;
    @Version
    private Long version;

    @RequiredArgsConstructor
    @Getter
    public enum Status {
        ACTIVE("Активна"),
        SCHEDULED("Запланирована"),
        EXPIRED("Истекла"),
        CANCELLED("Отменена");

        private final String commandText;
    }
}
