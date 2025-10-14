package com.kuklin.manageapp.bots.caloriebot.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.repository.DishRepository;
import com.kuklin.manageapp.common.library.models.openai.ChatModel;
import com.kuklin.manageapp.common.services.OpenAiIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DishService {
    private final DishRepository dishRepository;
    private final OpenAiIntegrationService openAiIntegrationService;
    private final TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents;
    private final ObjectMapper objectMapper;
    private static final String AI_PHOTO_REQUEST =
            """
                    Ты — система, которая анализирует фото.\s
                    На фото должно быть блюдо.\s
                    Если это не блюдо, верни JSON с полем "isDish": false, а остальные поля сделай null.\s
                                        
                    Если это блюдо или любая другая еда(напитки, снэки, конфеты и все съедобное), то дай ему имя, оцени примерное количество калорий, белков, жиров и углеводов. "isDish" : true\s
                    Верни JSON строго в формате:
                                        
                    {
                      "name": <String или null>
                      "calories": <Integer или null>,
                      "proteins": <Integer или null>,
                      "fats": <Integer или null>,
                      "carbohydrates": <Integer или null>,
                      "userId": <Integer или null>,
                      "isDish": <true|false>
                    }
                                        
                    ⚠️ Отвечай строго в формате JSON.
                       Не используй Markdown‑блоки, не добавляй ```json или ``` в начале и конце.
                       Не добавляй пояснений, текста или комментариев — только валидный JSON‑объект.
                       
                                        
                    """;

    private static final String AI_REQUEST =
            """
                    Ты — система, которая анализирует описание блюда.\s
                    В описании должно быть блюдо.\s
                    Если это не блюдо, верни JSON с полем "isDish": false, а остальные поля сделай null.\s
                                        
                    Если это блюдо или любая другая еда(напитки, снэки, конфеты и все съедобное), то дай ему имя, оцени примерное количество калорий, белков, жиров и углеводов. "isDish" : true\s
                    Верни JSON строго в формате:
                                        
                    {
                      "name": <String или null>
                      "calories": <Integer или null>,
                      "proteins": <Integer или null>,
                      "fats": <Integer или null>,
                      "carbohydrates": <Integer или null>,
                      "userId": <Integer или null>,
                      "isDish": <true|false>
                    }
                    
                    Описание: %s
                                        
                    ⚠️ Отвечай строго в формате JSON.
                       Не используй Markdown‑блоки, не добавляй ```json или ``` в начале и конце.
                       Не добавляй пояснений, текста или комментариев — только валидный JSON‑объект.
                       
                    """;
    public Dish createDishOrNull(DishDto dto) {
        if (dto.getUserId() == null) return null;
        return dishRepository.save(Dish.toEntity(dto));
    }

    public Dish getDishDtoByPhotoOrNull(Long userId, String imageUrl) {
        String aiResponse = openAiIntegrationService.fetchPhotoResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                AI_PHOTO_REQUEST, imageUrl);
        return getDishByAiResponseOrNull(userId, aiResponse);
    }

    public Dish getDishByDescriptionOrNull(Long userId, String text) {
        String aiResponse = openAiIntegrationService.fetchResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                String.format(AI_REQUEST, text));
        return getDishByAiResponseOrNull(userId, aiResponse);
    }

    public Map<ChatModel, DishDto> getDishDtoByPhotoOrNullWithManyModels(String imageUrl) {

        Map<ChatModel, String> raw = openAiIntegrationService.fetchResponseFromManyModels(
                telegramCaloriesBotKeyComponents.getAiKey(),
                AI_PHOTO_REQUEST,
                imageUrl
        );

        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<ChatModel, DishDto> result = new EnumMap<>(ChatModel.class);
        for (Map.Entry<ChatModel, String> e : raw.entrySet()) {
            DishDto dto = null;
            try {
                dto = readValue(e.getValue()); // парсим JSON/строку в DishDto
            } catch (Exception ex) {
                log.warn("Failed to parse DishDto for model {}: {}", e.getKey(), ex.getMessage());
            }
            result.put(e.getKey(), dto); // допускаем null по контракту метода
        }

        return result;

    }


    private Dish getDishByAiResponseOrNull(Long userId, String response) {
        DishDto dto = readValue(response).setUserId(userId);
        return dto.getIsDish() ?
                createDishOrNull(dto) :
                null
                ;
    }

    private DishDto readValue(String value) {
        try {
            return objectMapper.readValue(value, DishDto.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации");
            return null;
        }
    }

    public void removeByDishId(Long id) {
        dishRepository.deleteById(id);
    }

    public List<Dish> getTodayDishes(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        return dishRepository.findAllByUserIdAndCreatedBetween(userId, startOfDay, endOfDay);
    }

    public List<Dish> getWeekDishes(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDate.now()
                .minusDays(7)
                .atStartOfDay();
        LocalDateTime endOfDay = now;


        return dishRepository.findAllByUserIdAndCreatedBetween(userId, startOfDay, endOfDay);
    }
}
