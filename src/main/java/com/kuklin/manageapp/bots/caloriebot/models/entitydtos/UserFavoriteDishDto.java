package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserFavoriteDishDto {

    private Long id;

    private String name;
    private String emojiIcon;

    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;

    private Integer weightGrams;
    private Integer portions;
    private Integer portionWeight;

    private Dish.FoodCategory category;
    private Integer aiConfidence;

    private Long userId;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastUsedAt;

    // ================= ENTITY -> DTO =================

    public static UserFavoriteDishDto fromEntity(UserFavoriteDish e) {
        if (e == null) return null;

        return new UserFavoriteDishDto()
                .setId(e.getId())
                .setName(e.getName())
                .setEmojiIcon(e.getEmojiIcon())
                .setCalories(e.getCalories())
                .setProteins(e.getProteins())
                .setFats(e.getFats())
                .setCarbohydrates(e.getCarbohydrates())
                .setWeightGrams(e.getWeightGrams())
                .setPortions(e.getPortions())
                .setPortionWeight(e.getPortionWeight())
                .setCategory(e.getCategory())
                .setAiConfidence(e.getAiConfidence())
                .setUserId(e.getUserId())
                .setCreatedAt(e.getCreatedAt())
                .setUpdatedAt(e.getUpdatedAt())
                .setLastUsedAt(e.getLastUsedAt());
    }

    public static List<UserFavoriteDishDto> fromEntities(List<UserFavoriteDish> list) {
        if (list == null || list.isEmpty()) return List.of();

        return list.stream()
                .map(UserFavoriteDishDto::fromEntity)
                .toList();
    }

    // ================= DTO -> ENTITY =================

    public UserFavoriteDish toEntity(Long userId) {
        return new UserFavoriteDish()
                .setName(name)
                .setEmojiIcon(emojiIcon)
                .setCalories(calories)
                .setProteins(proteins)
                .setFats(fats)
                .setCarbohydrates(carbohydrates)
                .setWeightGrams(weightGrams)
                .setPortions(portions)
                .setPortionWeight(portionWeight)
                .setCategory(category)
                .setAiConfidence(aiConfidence)
                .setUserId(userId);
    }
}
