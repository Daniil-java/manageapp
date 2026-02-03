package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DishDto {

    // --- Основное ---
    private String name;
    private String emojiIcon;

    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;

    // --- Количество ---
    private Integer weightGrams;
    private Integer portions;
    private Integer portionWeight;

    // --- Классификация ---
    private Dish.FoodCategory category;     // fast_food / home_food / dessert / etc

    // --- ИИ ---
    private Integer aiConfidence; // 0–100

    // --- Системное ---
    private Long userId;
    private Boolean isDish;       // как и было: ИИ понял, что это блюдо

    @Override
    public String toString() {
        return String.format(
                "%s %s: %d ккал, Б: %dг, Ж: %dг, У: %dг",
                emojiIcon != null ? emojiIcon : "",
                name,
                nvl(calories),
                nvl(proteins),
                nvl(fats),
                nvl(carbohydrates)
        );
    }

    public String toStringSpecial() {
        return String.format(
                "%s %s: %d <b>ккал</b>, Б: <b>%d</b>г, Ж: <b>%d</b>г, У: <b>%d</b>г",
                emojiIcon != null ? emojiIcon : "",
                name,
                nvl(calories),
                nvl(proteins),
                nvl(fats),
                nvl(carbohydrates)
        );
    }

    private int nvl(Integer value) {
        return value != null ? value : 0;
    }
}
