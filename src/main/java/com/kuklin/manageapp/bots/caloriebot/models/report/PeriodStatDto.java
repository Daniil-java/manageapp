package com.kuklin.manageapp.bots.caloriebot.models.report;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class PeriodStatDto {
    private LocalDate date;
    private int calories;
    private int proteins;
    private int fats;
    private int carbs;
}
