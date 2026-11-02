package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfile;
import com.kuklin.manageapp.bots.caloriebot.entities.UserNutritionProfileEntry;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserNutritionProfileEntryDto {

    private Long userId;
    private UserNutritionProfile.Goal goal;
    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;

    private Instant validFrom;
    private Instant validTo;

    // ===================== ENTITY -> DTO =====================

    public static UserNutritionProfileEntryDto fromEntity(UserNutritionProfileEntry e) {
        if (e == null) return null;

        return new UserNutritionProfileEntryDto()
                .setUserId(e.getUserId())
                .setGoal(e.getGoal())
                .setCaloriesNormPerDay(e.getCaloriesNormPerDay())
                .setProteinsNormGramsPerDay(e.getProteinsNormGramsPerDay())
                .setFatsNormGramsPerDay(e.getFatsNormGramsPerDay())
                .setCarbsNormGramsPerDay(e.getCarbsNormGramsPerDay())
                .setWaterTargetMlPerDay(e.getWaterTargetMlPerDay())
                .setValidFrom(e.getValidFrom())
                .setValidTo(e.getValidTo());
    }

    // ===================== DTO -> ENTITY (CREATE) =====================

    public UserNutritionProfileEntry toEntity(Long userId) {
        return new UserNutritionProfileEntry()
                .setUserId(userId)
                .setGoal(goal)
                .setCaloriesNormPerDay(caloriesNormPerDay)
                .setProteinsNormGramsPerDay(proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(fatsNormGramsPerDay)
                .setCarbsNormGramsPerDay(carbsNormGramsPerDay)
                .setWaterTargetMlPerDay(waterTargetMlPerDay)
                .setValidFrom(validFrom)
                .setValidTo(validTo);
    }

    // ===================== MERGE (без затирания null) =====================

    public UserNutritionProfileEntry mergeToEntity(UserNutritionProfileEntry e) {
        if (e == null) {
            e = new UserNutritionProfileEntry();
        }

        if (goal != null) e.setGoal(goal);

        if (caloriesNormPerDay != null) e.setCaloriesNormPerDay(caloriesNormPerDay);
        if (proteinsNormGramsPerDay != null) e.setProteinsNormGramsPerDay(proteinsNormGramsPerDay);
        if (fatsNormGramsPerDay != null) e.setFatsNormGramsPerDay(fatsNormGramsPerDay);
        if (carbsNormGramsPerDay != null) e.setCarbsNormGramsPerDay(carbsNormGramsPerDay);
        if (waterTargetMlPerDay != null) e.setWaterTargetMlPerDay(waterTargetMlPerDay);

        if (validFrom != null) e.setValidFrom(validFrom);
        if (validTo != null) e.setValidTo(validTo);

        return e;
    }

    // ===================== FULL REPLACE (если вдруг надо) =====================

    public UserNutritionProfileEntry toEntity(UserNutritionProfileEntry existing) {
        UserNutritionProfileEntry e = (existing != null) ? existing : new UserNutritionProfileEntry();

        e.setGoal(goal);
        e.setCaloriesNormPerDay(caloriesNormPerDay);
        e.setProteinsNormGramsPerDay(proteinsNormGramsPerDay);
        e.setFatsNormGramsPerDay(fatsNormGramsPerDay);
        e.setCarbsNormGramsPerDay(carbsNormGramsPerDay);
        e.setWaterTargetMlPerDay(waterTargetMlPerDay);

        e.setValidFrom(validFrom);
        e.setValidTo(validTo);

        return e;
    }

    public static List<UserNutritionProfileEntryDto> fromEntities(List<UserNutritionProfileEntry> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        return list.stream()
                .map(UserNutritionProfileEntryDto::fromEntity)
                .toList();
    }

    public static List<UserNutritionProfileEntry> toEntities(List<UserNutritionProfileEntryDto> list, Long userId) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        return list.stream()
                .map(dto -> dto.toEntity(userId))
                .toList();
    }


}
