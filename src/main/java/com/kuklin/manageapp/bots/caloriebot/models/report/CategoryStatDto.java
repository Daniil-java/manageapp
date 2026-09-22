package com.kuklin.manageapp.bots.caloriebot.models.report;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CategoryStatDto {
    private String categoryName;
    private int calories;
    private double percentage;
}
