package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.AiInsightType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Последний сгенерированный ИИ-инсайт пользователя для страницы Insights
 * (саммари или шаблоны поведения за неделю / месяц).
 * На пару (пользователь, тип) — одна запись (уникальный индекс), при обновлении она перезаписывается.
 */
@Entity
@Table(name = "calorie_ai_insight")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CalorieAiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long appUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AiInsightType type;

    // Даты в таймзоне пользователя, обе включительно
    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    // Ответ ИИ как есть (JSON-объект, структура зависит от type). Строка уходит в jsonb без перекодирования
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    // Время последней генерации — запись одна на тип и перезаписывается при обновлении
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Язык текста инсайта ("en", "ru" — InsightLanguage.code); null — до миграции 0.0.55, английский. */
    @Column(name = "language", length = 8)
    private String language;
}
