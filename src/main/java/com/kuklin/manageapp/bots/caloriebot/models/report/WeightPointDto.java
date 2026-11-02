package com.kuklin.manageapp.bots.caloriebot.models.report;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class WeightPointDto {
    private LocalDate date;
    private BigDecimal weight;
}
