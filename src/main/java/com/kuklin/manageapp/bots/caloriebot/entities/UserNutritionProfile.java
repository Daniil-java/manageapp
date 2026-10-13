package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_nutrition_profiles")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserNutritionProfile {

    //Возраст
    public static final Integer AGE_MIN = 0;
    public static final Integer AGE_MAX = 140;
    //Рост
    public static final Integer HEIGHT_MIN = 30;
    public static final Integer HEIGHT_MAX = 280;
    //Вес
    public static final Integer WEIGHT_MIN = 1;
    public static final Integer WEIGHT_MAX = 500;
    //Вода
    public static final Integer DEF_WATER_ML = 2000;
    //Своя норма воды: как и калории — любое неотрицательное int
    public static final Integer WATER_NORM_MIN = 0;
    public static final Integer WATER_NORM_MAX = Integer.MAX_VALUE;
    //Ручная норма калорий: решает пользователь, ограничиваем только неотрицательностью и int
    public static final Integer CALORIES_NORM_MIN = 0;
    public static final Integer CALORIES_NORM_MAX = Integer.MAX_VALUE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Integer ageYears;
    private Integer heightCm;
    @Column(name = "current_weight_kg", precision = 5, scale = 2)
    private BigDecimal currentWeightKg;
    @Enumerated(EnumType.STRING)
    private Sex sex;
    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;
    @Enumerated(EnumType.STRING)
    private Goal goal;
    @Enumerated(EnumType.STRING)
    private DietType dietType;
    @Enumerated(EnumType.STRING)
    private UserProfileFillingState userProfileFillingState;
    // AUTO — норма калорий по формуле, MANUAL — задана пользователем
    @Enumerated(EnumType.STRING)
    @Column(name = "norm_mode", nullable = false)
    private NormMode normMode = NormMode.AUTO;
    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;
    // AUTO — норма воды по формуле (вес × мл/кг для активности), MANUAL — задана пользователем.
    // Независима от normMode: можно свои калории и воду по весу, и наоборот
    @Enumerated(EnumType.STRING)
    @Column(name = "water_mode", nullable = false)
    private NormMode waterMode = NormMode.AUTO;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;

    public enum UserProfileFillingState {
        EMPTY,      // только создан
        FILLING,    // процесс заполнения
        COMPLETED
    }

    /** Значение, которое пользователь выбирает кнопкой; подпись — в словаре bot_* по ключу getTextKey(). */
    public interface Labeled {
        String getTextKey();
    }

    public enum NormMode {
        AUTO,
        MANUAL
    }

    public boolean isManualNorm() {
        return normMode == NormMode.MANUAL;
    }

    public boolean isManualWater() {
        return waterMode == NormMode.MANUAL;
    }

    public enum DietType implements Labeled {
        REGULAR,
        LOW_CARB,
        VEGAN;

        @Override
        public String getTextKey() {
            return "profile.diet." + name();
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum Goal implements Labeled {
        LOSE_WEIGHT(0.8d, 2d, 0.9d),
        MAINTAIN(1d, 1.8d, 0.9d),
        GAIN_WEIGHT(1.15d, 1.8d, 0.9d);

        private final Double coef;
        private final Double proteinsPerKg;
        private final Double fatsPerKg;

        @Override
        public String getTextKey() {
            return "profile.goal." + name();
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum ActivityLevel implements Labeled {
        LOW(1.2d, 30),
        MEDIUM(1.55d, 33),
        HIGH(1.75d, 35);

        private final Double coef;
        private final Integer waterMlPerKg;

        @Override
        public String getTextKey() {
            return "profile.activity." + name();
        }
    }

    public enum Sex implements Labeled {
        MALE,
        FEMALE;

        @Override
        public String getTextKey() {
            return "profile.sex." + name();
        }
    }


    public UserNutritionProfile copy() {
        return new UserNutritionProfile()
                .setId(this.id)
                .setUserId(this.userId)
                .setAgeYears(this.ageYears)
                .setHeightCm(this.heightCm)
                .setCurrentWeightKg(this.currentWeightKg)
                .setSex(this.sex)
                .setActivityLevel(this.activityLevel)
                .setGoal(this.goal)
                .setDietType(this.dietType)
                .setUserProfileFillingState(this.userProfileFillingState)
                .setNormMode(this.normMode)
                .setCaloriesNormPerDay(this.caloriesNormPerDay)
                .setProteinsNormGramsPerDay(this.proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(this.fatsNormGramsPerDay)
                .setCarbsNormGramsPerDay(this.carbsNormGramsPerDay)
                .setWaterTargetMlPerDay(this.waterTargetMlPerDay)
                .setWaterMode(this.waterMode)
                .setCreatedAt(this.createdAt)
                .setUpdatedAt(this.updatedAt);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("UserNutritionProfile:\n");
        sb.append("userId: ").append(userId).append("\n");
        sb.append("sex: ").append(sex).append("\n");
        sb.append("ageYears: ").append(ageYears).append("\n");
        sb.append("heightCm: ").append(heightCm).append("\n");
        sb.append("currentWeightKg: ").append(currentWeightKg != null ? currentWeightKg.stripTrailingZeros().toPlainString() : "null").append("\n");
        sb.append("activityLevel: ").append(activityLevel).append("\n");
        sb.append("goal: ").append(goal).append("\n");
        sb.append("normMode: ").append(normMode).append("\n");
        sb.append("dietType: ").append(dietType).append("\n");
        sb.append("caloriesTarget: ").append(caloriesNormPerDay).append("\n");
        sb.append("proteinsTarget: ").append(proteinsNormGramsPerDay).append("\n");
        sb.append("fatsTarget: ").append(fatsNormGramsPerDay).append("\n");
        sb.append("carbsTarget: ").append(carbsNormGramsPerDay).append("\n");
        sb.append("waterTargetMl: ").append(waterTargetMlPerDay).append("\n");
        sb.append("waterMode: ").append(waterMode).append("\n");
        sb.append("fillingState: ").append(userProfileFillingState);
        return sb.toString();
    }

    public Boolean checkTargetCalculateParams() {
        if (sex == null) return false;
        if (ageYears == null) return false;
        if (heightCm == null) return false;
        if (currentWeightKg == null) return false;
        if (activityLevel == null) return false;
        if (goal == null) return false;

        return true;
    }

    public UserNutritionProfileEntry toProfileEntryWithoutValidTime() {
        return new UserNutritionProfileEntry()
                .setUserId(this.userId)
                .setGoal(this.goal)
                .setCaloriesNormPerDay(this.caloriesNormPerDay)
                .setProteinsNormGramsPerDay(this.proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(this.fatsNormGramsPerDay)
                .setCarbsNormGramsPerDay(this.carbsNormGramsPerDay)
                .setWaterTargetMlPerDay(this.waterTargetMlPerDay)
                ;
    }
}
