package com.kuklin.manageapp.bots.caloriebot.entities.models;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserNutritionDto {
    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;

    public static UserNutritionProfile updateNutritionData(UserNutritionProfile profile, UserNutritionDto dto) {
        return profile
                .setCaloriesNormPerDay(dto.getCaloriesNormPerDay())
                .setProteinsNormGramsPerDay(dto.proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(dto.getFatsNormGramsPerDay())
                .setCarbsNormGramsPerDay(dto.getCarbsNormGramsPerDay())
                .setWaterTargetMlPerDay(dto.waterTargetMlPerDay)
                ;
    }
}
