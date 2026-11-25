package com.kuklin.manageapp.bots.deparrbot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Личный токен пользователя для REST API авиабота (виджет). Один на пользователя. */
@Entity
@Table(name = "avia_api_tokens")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AviaApiToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    /** SHA-256 (hex) от токена — сам токен не храним */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
