package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность для отслеживания текущего потребления лимитов пользователем.
 * Хранит информацию о том, сколько раз была использована конкретная функция
 * в рамках текущего расчетного периода (день, месяц и т.д.).
 */
@Entity
@Table(name = "user_feature_usage")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserFeatureUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;

    @Enumerated(EnumType.STRING)
    private BotFeature feature;

    private int usedCount = 0;

    /** * Календарная дата последнего сброса лимита.
     * Используется для логики DAILY лимитов: если текущая LocalDate больше этой,
     * значит наступил новый день и usedCount нужно обнулить.
     */
    private LocalDate lastResetLocalDate;

    // "Физическое" время последнего сброса (например, 2023-10-24 21:00:00 UTC)
    private LocalDateTime lastResetUtc;
}
