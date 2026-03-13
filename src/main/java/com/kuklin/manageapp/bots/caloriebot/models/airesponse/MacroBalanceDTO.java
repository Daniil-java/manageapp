package com.kuklin.manageapp.bots.caloriebot.models.airesponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MacroBalanceDTO {
    private double proteinPercentage;
    private double fatPercentage;
    private double carbPercentage;
    private String imbalanceAnalysis;
    private String suggestedRatio;
}
