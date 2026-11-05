package com.kuklin.manageapp.bots.caloriebot.support;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Заявка в поддержку из /support: в группе поддержки — своя тема (topicId), переписка — {@link SupportMessage}.
 */
@Entity
@Table(name = "support_ticket")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appUserId;
    @Enumerated(EnumType.STRING)
    private Category category;
    @Enumerated(EnumType.STRING)
    private Status status;
    // Тема в группе поддержки; null — заявка ещё DRAFT
    private Integer topicId;
    // Ждём сообщение пользователя в заявку с этого момента; null — не ждём
    private Instant awaitingTextSince;
    private Instant lastUserMessageAt;
    private Instant lastSupportMessageAt;
    // Последнее напоминание в группе «без ответа»: одно на каждое новое сообщение пользователя
    private Instant remindedAt;
    // Оценка после закрытия: 1 — 👍, -1 — 👎
    private Short rating;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant closedAt;

    public enum Status {
        DRAFT, OPEN, CLOSED
    }

    /** Категория — эмодзи в теме группы и в кнопках; подписи для пользователя — support.category.* в bot_*. */
    @Getter
    @RequiredArgsConstructor
    public enum Category {
        PAYMENT("💳"),
        BUG("🐞"),
        QUESTION("❓"),
        IDEA("💡"),
        OTHER("✉️");

        private final String emoji;
    }
}
