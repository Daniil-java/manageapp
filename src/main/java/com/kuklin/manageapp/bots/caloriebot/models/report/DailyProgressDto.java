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
    private int fatsTarget;
    private int carbsFact;
    private int carbsTarget;
    private int waterFact;
    private int waterTarget;
    private int streak;
}
