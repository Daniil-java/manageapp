package com.kuklin.manageapp.bots.caloriebot.entities.models;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DishDto {
    private String name;
    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;
    private Long userId;
    private Boolean isDish;

    @Override
    public String toString() {
        return String.format("%s: %d ккал, Б: %dг, Ж: %dг, У: %dг",
                name, calories, proteins, fats, carbohydrates);
    }
}
