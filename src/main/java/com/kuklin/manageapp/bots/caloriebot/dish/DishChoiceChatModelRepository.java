package com.kuklin.manageapp.bots.caloriebot.dish;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishChoiceChatModelRepository extends JpaRepository<DishChoiceChatModel, Long> {
    long countByChatModel(ChatModel chatModel);
}
