package com.kuklin.manageapp.bots.caloriebot.entities;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
    private LocalDateTime created;

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
        StringBuilder sb = new StringBuilder();
        sb.append("🍽 <b>").append(dish.getName()).append("</b> ")
                .append("🔥 Ккал: <b>").append(dish.getCalories()).append("</b> ")
                .append("🥩 Б: <b>").append(dish.getProteins()).append(" г</b> ")
                .append("🥑 Ж: <b>").append(dish.getFats()).append(" г</b> ")
                .append("🍞 У: <b>").append(dish.getCarbohydrates()).append(" г</b>\n");

        return sb.toString();
    }
}
