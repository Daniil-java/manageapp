package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.repository.DishChoiceChatModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DishChoiceChatModelService {
    private final DishChoiceChatModelRepository dishChoiceChatModelRepository;

    public DishChoiceChatModel createChoice(DishChoiceChatModel dishChoiceChatModel) {
        return dishChoiceChatModelRepository.save(dishChoiceChatModel);
    }

    public String getStatistics() {
        Long count = dishChoiceChatModelRepository.count();

        StringBuilder sb = new StringBuilder();
        sb.append("Всего блюд оценено: ").append(count).append("\n");

        for (ChatModel chatModel: ChatModel.getModels()) {
            long modelCount = dishChoiceChatModelRepository.countByChatModel(chatModel);

            sb.append(chatModel.getName()).append(": ").append(modelCount).append("\n");
        }

        return sb.toString();
    }
}
