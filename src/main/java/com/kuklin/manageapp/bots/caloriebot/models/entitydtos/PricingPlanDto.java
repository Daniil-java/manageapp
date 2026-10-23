package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.payment.entities.PricingPlan;
import com.kuklin.manageapp.payment.models.common.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тариф для миниаппки: title и description уже на языке пользователя (PlanTexts).
 * Имена полей — как у сущности PricingPlan, которую API отдавал раньше: фронт менять не нужно.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingPlanDto {
    private Long id;
    private String codeForOrderId;
    private String title;
    /** Пункты по строкам («эмодзи текст»), как на лендинге. */
    private String description;
    private Currency currency;
    /** Для XTR — звёзды. */
    private Integer priceMinor;
    private PricingPlan.PricingPlanType payloadType;
    private Integer durationDays;
}
