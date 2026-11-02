package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_nutrition_profile_entries")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserNutritionProfileEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private UserNutritionProfile.Goal goal;

    private Integer caloriesNormPerDay;
    private Integer proteinsNormGramsPerDay;
    private Integer fatsNormGramsPerDay;
    private Integer carbsNormGramsPerDay;
    private Integer waterTargetMlPerDay;

    // --- Интервал действия ---
    @Column(nullable = false)
    private Instant validFrom;

    @Column
    private Instant validTo; // null = активная цель

    @UpdateTimestamp
    private Instant updatedAt;
    @CreationTimestamp
    private Instant createdAt;

    public UserNutritionProfile toProfile(UserNutritionProfile profile) {
        profile
                .setGoal(this.goal)
                .setCaloriesNormPerDay(this.caloriesNormPerDay)
                .setProteinsNormGramsPerDay(this.proteinsNormGramsPerDay)
                .setFatsNormGramsPerDay(this.fatsNormGramsPerDay)
                .setCarbsNormGramsPerDay(this.carbsNormGramsPerDay)
                .setWaterTargetMlPerDay(this.waterTargetMlPerDay);

        return profile;
    }

    public boolean targetsEquals(UserNutritionProfileEntry other) {
        if (other == null) return false;

        return Objects.equals(goal, other.goal)
                && Objects.equals(caloriesNormPerDay, other.caloriesNormPerDay)
                && Objects.equals(proteinsNormGramsPerDay, other.proteinsNormGramsPerDay)
                && Objects.equals(fatsNormGramsPerDay, other.fatsNormGramsPerDay)
                && Objects.equals(carbsNormGramsPerDay, other.carbsNormGramsPerDay)
                && Objects.equals(waterTargetMlPerDay, other.waterTargetMlPerDay);
    }
}
