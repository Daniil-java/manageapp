package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.validator.constraints.Range;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Accessors(chain = true)
public class UserNutritionProfileDto {
    private UserNutritionProfile.Sex sex;
    @Range(min = 1, max = 120, message = "Возраст должен быть от 10 до 120 лет")
    private Integer ageYears;
    @Range(min = 50, max = 250, message = "Рост должен быть от 50 до 250 см")
    private Integer heightCm;
    @DecimalMin(value = "20.0", message = "Вес должен быть не менее 20 кг")
    private BigDecimal currentWeightKg;
    private UserNutritionProfile.ActivityLevel activityLevel;
    private UserNutritionProfile.Goal goal;
    private UserNutritionProfile.DietType dietType;
    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;

    public static UserNutritionProfile updateNutritionData(UserNutritionProfile profile, UserNutritionProfileDto dto) {
        return profile
                .setCaloriesNormPerDay(dto.getCaloriesNormPerDay())
                .setProteinsNormGramsPerDay(dto.proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(dto.getFatsNormGramsPerDay())
                .setCarbsNormGramsPerDay(dto.getCarbsNormGramsPerDay())
                .setWaterTargetMlPerDay(dto.waterTargetMlPerDay)
                ;
    }

    public UserNutritionProfile toEntity(Long userId) {
        return new UserNutritionProfile()
                .setUserId(userId)
                .setSex(sex)
                .setAgeYears(ageYears)
                .setHeightCm(heightCm)
                .setCurrentWeightKg(currentWeightKg)
                .setActivityLevel(activityLevel)
                .setGoal(goal)
                .setDietType(dietType)
                .setCaloriesNormPerDay(caloriesNormPerDay)
                .setProteinsNormGramsPerDay(proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(fatsNormGramsPerDay)
                .setCarbsNormGramsPerDay(carbsNormGramsPerDay)
                .setWaterTargetMlPerDay(waterTargetMlPerDay);
    }

    public static UserNutritionProfileDto fromEntity(UserNutritionProfile p) {
        if (p == null) return null;

        return new UserNutritionProfileDto()
                .setSex(p.getSex())
                .setAgeYears(p.getAgeYears())
                .setHeightCm(p.getHeightCm())
                .setCurrentWeightKg(p.getCurrentWeightKg())
                .setActivityLevel(p.getActivityLevel())
                .setGoal(p.getGoal())
                .setDietType(p.getDietType())
                .setCaloriesNormPerDay(p.getCaloriesNormPerDay())
                .setProteinsNormGramsPerDay(p.getProteinsNormGramsPerDay())
                .setFatsNormGramsPerDay(p.getFatsNormGramsPerDay())
                .setCarbsNormGramsPerDay(p.getCarbsNormGramsPerDay())
                .setWaterTargetMlPerDay(p.getWaterTargetMlPerDay());
    }

    public UserNutritionProfile mergeToEntity(UserNutritionProfile profile) {
        if (profile == null) {
            profile = new UserNutritionProfile();
        }

        if (sex != null) profile.setSex(sex);
        if (ageYears != null) profile.setAgeYears(ageYears);
        if (heightCm != null) profile.setHeightCm(heightCm);
        if (currentWeightKg != null) profile.setCurrentWeightKg(currentWeightKg);

        if (activityLevel != null) profile.setActivityLevel(activityLevel);
        if (goal != null) profile.setGoal(goal);
        if (dietType != null) profile.setDietType(dietType);

        if (caloriesNormPerDay != null) profile.setCaloriesNormPerDay(caloriesNormPerDay);
        if (proteinsNormGramsPerDay != null) profile.setProteinsNormGramsPerDay(proteinsNormGramsPerDay);
        if (fatsNormGramsPerDay != null) profile.setFatsNormGramsPerDay(fatsNormGramsPerDay);
        if (carbsNormGramsPerDay != null) profile.setCarbsNormGramsPerDay(carbsNormGramsPerDay);
        if (waterTargetMlPerDay != null) profile.setWaterTargetMlPerDay(waterTargetMlPerDay);

        return profile;
    }
}
