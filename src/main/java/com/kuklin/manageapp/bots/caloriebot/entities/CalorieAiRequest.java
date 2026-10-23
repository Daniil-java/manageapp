package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Одно обращение пользователя к ИИ (текст, фото, голос, отчёт, инсайт) — по ним AiRateLimiter
 * считает лимиты за минуту и за сутки. Засчитываются попытки, а не успешные ответы.
 */
@Entity
@Table(name = "calorie_ai_request")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CalorieAiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long appUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
