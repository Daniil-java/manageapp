package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_favorite_dishes")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserFavoriteDish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Основное ---
    private String name;
    private String emojiIcon;          // 🍕 🥗 ☕️ — для UI

    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;

    // --- Количество ---
    private Integer weightGrams;       // фактический вес блюда
    private Integer portions;          // количество порций
    private Integer portionWeight;     // вес одной порции (если известно)

    // --- Классификация ---
    @Enumerated(EnumType.STRING)
    private Dish.FoodCategory category;           // fast_food / home_food / dessert / etc

    // --- ИИ ---
    private Integer aiConfidence;      // 0–100, насколько ИИ уверен

    // --- Системное ---
    private Long userId;
    @CreationTimestamp
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
    private Instant lastUsedAt;

    public static Dish toDish(UserFavoriteDish fav) {
        if (fav == null) {
            return null;
        }

        Dish dish = new Dish();

        dish.setName(fav.getName());
        dish.setEmojiIcon(fav.getEmojiIcon());

        dish.setCalories(fav.getCalories());
        dish.setProteins(fav.getProteins());
        dish.setFats(fav.getFats());
        dish.setCarbohydrates(fav.getCarbohydrates());

        dish.setWeightGrams(fav.getWeightGrams());
        dish.setPortions(fav.getPortions());
        dish.setPortionWeight(fav.getPortionWeight());

        dish.setAiConfidence(fav.getAiConfidence());
        dish.setUserId(fav.getUserId());

        dish.setCategory(
                fav.getCategory() != null
                        ? Dish.FoodCategory.valueOf(fav.getCategory().name())
                        : Dish.FoodCategory.UNKNOWN
        );

        return dish;
    }
}
