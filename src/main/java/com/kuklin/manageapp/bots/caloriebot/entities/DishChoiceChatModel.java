package com.kuklin.manageapp.bots.caloriebot.entities;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "dish_choice_chat_model")
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DishChoiceChatModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long dishId;
    @Enumerated(EnumType.STRING)
    private ChatModel chatModel;
    private String name;
    private Integer calories;
    private Integer proteins;
    private Integer fats;
    private Integer carbohydrates;
    private Boolean isChoosed;
}
