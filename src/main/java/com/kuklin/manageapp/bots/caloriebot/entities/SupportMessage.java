package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Сообщение в заявке поддержки — от пользователя или от поддержки. */
@Entity
@Table(name = "support_message")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class SupportMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticketId;
    @Enumerated(EnumType.STRING)
    private Direction direction;
    // Текст или подпись к фото; фото без подписи — «[фото]»
    private String text;
    // id сообщения в чате пользователя: ответ (reply) на сообщение поддержки находит по нему заявку
    private Integer userMessageId;
    private Integer groupMessageId;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum Direction {
        USER, SUPPORT
    }
}
