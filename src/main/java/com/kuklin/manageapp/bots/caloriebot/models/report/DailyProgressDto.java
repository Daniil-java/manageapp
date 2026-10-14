package com.kuklin.manageapp.bots.caloriebot.models.report;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DailyProgressDto {
    private int caloriesFact;
    private Integer caloriesTarget;
    private int proteinsFact;
    private Integer proteinsTarget;
    private int fatsFact;
    private Integer fatsTarget;
    private int carbsFact;
    private Integer carbsTarget;
    private int waterFact;
    private int waterTarget;
    private int streak;
}
