package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
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
    private String name;
    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;
    private Long userId;

    @CreationTimestamp
    private Instant created;

    public static Dish toEntity(DishDto dto) {
        return new Dish()
                .setName(dto.getName())
                .setCalories(dto.getCalories())
                .setProteins(dto.getProteins())
                .setFats(dto.getFats())
                .setCarbohydrates(dto.getCarbohydrates())
                .setUserId(dto.getUserId())
                ;
    }

    public static String getInfo(Dish dish) {
        if (dish == null) {
            return "❌ Нет данных о блюде";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("🍽 <b>").append(dish.getName() != null ? dish.getName() : "Без названия").append("</b> ").append("\n")
                .append("🔥 Ккал: <b>").append(dish.getCalories() != null ? dish.getCalories() : 0).append("</b> ")
                .append("🥩 Б: <b>").append(dish.getProteins() != null ? dish.getProteins() : 0).append(" г</b> ")
                .append("🥑 Ж: <b>").append(dish.getFats() != null ? dish.getFats() : 0).append(" г</b> ")
                .append("🍞 У: <b>").append(dish.getCarbohydrates() != null ? dish.getCarbohydrates() : 0).append(" г</b>\n");

        return sb.toString();
    }

    /**
     * Смещает КБЖУ на заданный процент.
     * percentDelta: -50, -10, 10, 50 и т.п.
     * -50  => умножить на 0.5
     *  50  => умножить на 1.5
     */
    public Dish applyPercentDelta(int percentDelta) {
        // 1 + (-50 / 100) = 0.5; 1 + 50 / 100 = 1.5
        double multiplier = 1.0 + (percentDelta / 100.0);

        // не даём уйти в минус (если прилетит <= -100)
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        this.calories      = scale(this.calories,      multiplier);
        this.proteins      = scale(this.proteins,      multiplier);
        this.fats          = scale(this.fats,          multiplier);
        this.carbohydrates = scale(this.carbohydrates, multiplier);

        return this;
    }

    private Integer scale(Integer value, double multiplier) {
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

}
