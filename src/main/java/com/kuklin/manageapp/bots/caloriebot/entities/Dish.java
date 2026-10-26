package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "dishes")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Dish {

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
    @Column(name = "weight")
    private Integer weight;       // фактический вес блюда
    private Integer portions;          // количество порций
    private Integer portionWeight;     // вес одной порции (если известно)

    // --- Классификация ---
    @Enumerated(EnumType.STRING)
    private FoodCategory category;           // fast_food / home_food / dessert / etc

    // --- ИИ ---
    private Integer aiConfidence;      // 0–100, насколько ИИ уверен

    // --- Системное ---
    private Long userId;

    @CreationTimestamp
    private Instant created;

    /** Категория блюда. Название для пользователя — в словарях i18n/calorie/bot_* по ключу {@link #getTextKey()}. */
    public enum FoodCategory {
        MAIN_COURSE,
        SIDE_DISH,
        SOUP,
        SALAD,
        SNACK,
        DESSERT,
        DRINK,
        BREAKFAST_ITEM,
        BREAD_BAKERY,
        FAST_FOOD,
        SAUCE_DIP,
        UNKNOWN;

        public String getTextKey() {
            return "dish.category." + name();
        }
    }


    public static Dish toEntity(DishDto dto) {
        return new Dish()
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

    /**
     * Смещает КБЖУ на заданный процент.
     * percentDelta: -50, -10, 10, 50 и т.п.
     */
    public Dish applyPercentDelta(int percentDelta) {
        double multiplier = 1.0 + (percentDelta / 100.0);
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        this.calories      = scale(this.calories, multiplier);
        this.proteins      = scale(this.proteins, multiplier);
        this.fats          = scale(this.fats, multiplier);
        this.carbohydrates = scale(this.carbohydrates, multiplier);

        return this;
    }

    public Dish applyPortionWeightPercentDelta(int percentDelta) {
        if (portionWeight == null || portions == null || portions <= 0) {
            // Нечего пересчитывать — данных нет
            return this;
        }

        double multiplier = 1.0 + percentDelta / 100.0;
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        // 1. Меняем вес одной порции
        this.portionWeight = scale(this.portionWeight, multiplier);

        // 2. Пересчитываем общий вес
        // scale, а не умножение int: при огромном числе порций вес упрётся в максимум, а не уйдёт в минус
        this.weight = scale(this.portionWeight, this.portions);

        // 3. Масштабируем КБЖУ
        this.calories      = scale(this.calories, multiplier);
        this.proteins      = scale(this.proteins, multiplier);
        this.fats          = scale(this.fats, multiplier);
        this.carbohydrates = scale(this.carbohydrates, multiplier);

        return this;
    }

    public static Integer scale(Integer value, double multiplier) {
        if (value == null) {
            return null;
        }

        long result = Math.round(value * multiplier);

        if (result < 0) {
            result = 0;
        } else if (result > Integer.MAX_VALUE) {
            result = Integer.MAX_VALUE;
        }

        return (int) result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dish{");
        sb.append("name='").append(name).append("'");
        sb.append(", cal=").append(calories);
        sb.append(", p=").append(proteins);
        sb.append(", f=").append(fats);
        sb.append(", c=").append(carbohydrates);
        sb.append(", weight=").append(weight);
        sb.append(", cat=").append(category);
        sb.append(", conf=").append(aiConfidence);
        sb.append(", created=").append(created); // Instant в формате UTC ISO-8601
        sb.append("}");
        return sb.toString();
    }
}
