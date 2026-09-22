package com.kuklin.manageapp.bots.caloriebot.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlanDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer durationDays;
}
