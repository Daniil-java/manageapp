package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Состояние обновления ИИ-инсайта на пару (пользователь, тип): идёт ли генерация прямо сейчас
 * и когда последний раз обращались к ИИ. Читается и меняется только запросами
 * CalorieAiInsightRefreshRepository — атомарно, чтобы два параллельных нажатия не запустили две генерации.
 */
@Entity
@Table(name = "calorie_ai_insight_refresh")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CalorieAiInsightRefresh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long appUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AiInsightType type;

    // Генерация идёт с этого момента; null — сейчас не идёт
    @Column(name = "started_at")
    private Instant startedAt;

    // Последнее обращение к ИИ, успешное или нет — от него отсчитывается пауза между обновлениями
    @Column(name = "ai_called_at")
    private Instant aiCalledAt;
}
