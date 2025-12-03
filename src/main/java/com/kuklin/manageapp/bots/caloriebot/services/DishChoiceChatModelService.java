package com.kuklin.manageapp.bots.caloriebot.services;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.repository.DishChoiceChatModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        for (ChatModel chatModel : ChatModel.getModels()) {
            long modelCount = dishChoiceChatModelRepository.countByChatModel(chatModel);

            sb.append(chatModel.getName()).append(": ").append(modelCount).append("\n");
        }

        return sb.toString();
    }

    public List<DishChoiceChatModel> saveList(Map<ChatModel, DishDto> dishDtos, Long dishId) {
        List<DishChoiceChatModel> dishChoiceChatModels = new ArrayList<>();
        for (Map.Entry<ChatModel, DishDto> entry : dishDtos.entrySet()) {
            ChatModel model = entry.getKey();
            DishDto dto = entry.getValue();

            DishChoiceChatModel dishChoiceChatModel = createChoice(
                    new DishChoiceChatModel()
                            .setChatModel(model)
                            .setDishId(dishId)
                            .setProteins(dto.getProteins())
                            .setCarbohydrates(dto.getCarbohydrates())
                            .setFats(dto.getFats())
                            .setCalories(dto.getCalories())
                            .setName(dto.getName())
                            .setIsChoosed(false)
            );

            dishChoiceChatModels.add(dishChoiceChatModel);
        }
        return dishChoiceChatModels;
    }

    public DishChoiceChatModel setChoiceTrueOrNull(Long id) {
        DishChoiceChatModel dishChoice = dishChoiceChatModelRepository.findById(id)
                .orElse(null);

        return dishChoice == null
        ? null
        : dishChoiceChatModelRepository.save(dishChoice.setIsChoosed(true));

    }
}
