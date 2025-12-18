package com.kuklin.manageapp.bots.caloriebot.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_favorite_dishes")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class UserFavoriteDish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String name;

    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
}
