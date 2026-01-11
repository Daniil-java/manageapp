package com.kuklin.manageapp.bots.caloriebot.repository;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishChoiceChatModelRepository extends JpaRepository<DishChoiceChatModel, Long> {
    long countByChatModel(ChatModel chatModel);
}
