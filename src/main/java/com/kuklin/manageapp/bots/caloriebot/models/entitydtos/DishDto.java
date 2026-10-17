package com.kuklin.manageapp.bots.caloriebot.models.entitydtos;

import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class DishDto {

    // --- Основное ---
    private Long id;
    @NotBlank(message = "Название блюда не может быть пустым")
    private String name;
    private String emojiIcon;

    @PositiveOrZero(message = "Калории не могут быть отрицательными")
    private Integer calories;
    @PositiveOrZero(message = "Белки не могут быть отрицательными")
    private Integer proteins;
    @PositiveOrZero(message = "Жиры не могут быть отрицательными")
    private Integer fats;
    @PositiveOrZero(message = "Углеводы не могут быть отрицательными")
    private Integer carbohydrates;

    // --- Количество ---
    private Integer weight;
    @Min(value = 1, message = "Количество порций должно быть не менее 1")
    private Integer portions;
    @PositiveOrZero(message = "Вес не может быть отрицательными")
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

    public Dish mergeToEntity(Dish dish) {
        if (dish == null) {
            dish = new Dish();
        }

        if (id != null) dish.setId(id);
        if (name != null) dish.setName(name);
        if (emojiIcon != null) dish.setEmojiIcon(emojiIcon);

        if (calories != null) dish.setCalories(calories);
        if (proteins != null) dish.setProteins(proteins);
        if (fats != null) dish.setFats(fats);
        if (carbohydrates != null) dish.setCarbohydrates(carbohydrates);

        if (weight != null) dish.setWeight(weight);
        if (portions != null) dish.setPortions(portions);
        if (portionWeight != null) dish.setPortionWeight(portionWeight);

        if (category != null) dish.setCategory(category);
        if (aiConfidence != null) dish.setAiConfidence(aiConfidence);

        // системное — только при создании
        if (dish.getId() == null && userId != null) {
            dish.setUserId(userId);
        }

        return dish;
    }

    public void checkValuesNotNull() {
        if (calories == null) calories = 0;
        if (proteins == null) proteins = 0;
        if (fats == null) fats = 0;
        if (carbohydrates == null) carbohydrates = 0;

        //значения, которые не могут быть меньше единицы
        if (weight == null) weight = 1;
        if (portions == null) portions = 1;
        if (portionWeight == null) portionWeight = 1;

        if (aiConfidence == null) aiConfidence = 0;
    }

    // ===================== DTO -> ENTITY =====================

    public static Dish toEntity(DishDto dto) {
        if (dto == null) {
            return null;
        }

        return new Dish()
                .setId(dto.getId())
                .setName(dto.getName())
                .setEmojiIcon(dto.getEmojiIcon())
                .setCalories(dto.getCalories())
                .setProteins(dto.getProteins())
                .setFats(dto.getFats())
                .setCarbohydrates(dto.getCarbohydrates())
                .setWeight(dto.getWeight())
                .setPortions(dto.getPortions())
                .setPortionWeight(dto.getPortionWeight())
                .setCategory(dto.getCategory())
                .setAiConfidence(dto.getAiConfidence())
                .setUserId(dto.getUserId());
    }

    public static List<Dish> toEntities(List<DishDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }

        return dtos.stream()
                .map(DishDto::toEntity)
                .collect(Collectors.toList());
    }


// ===================== ENTITY -> DTO =====================

    public static DishDto fromEntity(Dish entity) {
        if (entity == null) {
            return null;
        }

        return new DishDto()
                .setId(entity.getId())
                .setName(entity.getName())
                .setEmojiIcon(entity.getEmojiIcon())
                .setCalories(entity.getCalories())
                .setProteins(entity.getProteins())
                .setFats(entity.getFats())
                .setCarbohydrates(entity.getCarbohydrates())
                .setWeight(entity.getWeight())
                .setPortions(entity.getPortions())
                .setPortionWeight(entity.getPortionWeight())
                .setCategory(entity.getCategory())
                .setAiConfidence(entity.getAiConfidence())
                .setUserId(entity.getUserId());
    }

    public static List<DishDto> fromEntities(List<Dish> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        return entities.stream()
                .map(DishDto::fromEntity)
                .collect(Collectors.toList());
    }
}
