package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.models.feature.FeatureLimitPeriod;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Сущность, описывающая настройки и ограничения конкретной функции (Feature)
 * внутри определенного тарифного плана.
 */
@Entity
@Table(name = "plan_features")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** * Код тарифного плана (например, 'FREE', 'PREMIUM_MONTH').
     * Связывает настройки с планом без жесткой зависимости на таблицу планов.
     */
    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false)
    private BotFeature feature;

    @Enumerated(EnumType.STRING)
    private BotIdentifier botIdentifier;

    @Column(name = "limit_value")
    private Integer limitValue;

    /** * Численное значение лимита (количество разрешенных действий).
     * Значение null или -1 обычно трактуется как отсутствие лимита.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_period")
    private FeatureLimitPeriod limitPeriod;

}
