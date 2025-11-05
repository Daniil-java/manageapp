package com.kuklin.manageapp.bots.caloriebot.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessorHandler;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.repository.DishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DishService {
    private final DishRepository dishRepository;
    private final OpenAiProviderProcessor openAiIntegrationService;
    private final TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents;
    private final ObjectMapper objectMapper;
    private final ProviderProcessorHandler processorHandler;
    private static final String AI_PHOTO_REQUEST =
            """
                    Ты — экспертная система анализа изображений еды и напитков на фото. \s
                                                Твоя задача — определить, есть ли на фото что-то съедобное (блюдо, продукт, напиток, упаковка готовой еды, снэк, конфета и т.п.), и если да — оценить состав и калорийность.
                                                
                                                Инструкции:
                                                
                                                1. Если на фото нет ничего, что можно съесть или выпить (пейзаж, человек, предмет, животное и т.д.) —\s
                                                   верни JSON:
                                                   {
                                                     "name": null,
                                                     "calories": null,
                                                     "proteins": null,
                                                     "fats": null,
                                                     "carbohydrates": null,
                                                     "userId": null,
                                                     "isDish": false
                                                   }
                                                
                                                2. Если на фото есть еда (в любой форме):
                                                   - Укажи **точное или ближайшее название продукта** (если виден бренд — укажи его, например "Coca-Cola 0.5L", "Burger King Whopper", "Snickers").
                                                   - Определи, является ли это:
                                                     - а) домашним/рестораным блюдом (оцени по внешнему виду);
                                                     - б) упакованным продуктом (если виден бренд, упаковка, логотип, этикетка и т.п.);
                                                   - Если это **упаковка или брендовый продукт**, оцени калорийность и БЖУ **на всю упаковку**, если возможно (например, если на фото бутылка 0.5L — рассчитай для всего объёма).
                                                   - Если это **готовое блюдо без упаковки**, оцени значения исходя из типичной порции, видимой на изображении.
                                                   - Все значения (калории, белки, жиры, углеводы) возвращай как **приблизительные целые числа** (без единиц измерения).
                                                   - Если ты не уверен, оцени по наиболее вероятному типичному варианту.
                                        
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
                       Значения должны быть **адекватно оценены** на основании визуальной информации (включая бренды, упаковку, порцию и тип продукта).
                                        
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

    public Map<ChatModel, DishDto> getDishDtoByPhotoOrNullWithManyProviders(String imageUrl) {
        Map<ChatModel, DishDto> map = new HashMap<>();
        for (ChatModel chatModel : ChatModel.getModels()) {
            ProviderVariant provider = chatModel.getProviderVariant();

            String aiKey = null;
            switch (chatModel.getProviderVariant()) {
                case CLAUDE -> aiKey = telegramCaloriesBotKeyComponents.getClaudeAiKey();
                case GEMINI -> aiKey = telegramCaloriesBotKeyComponents.getGeminiAiKey();
                case DEEPSEEK -> aiKey = telegramCaloriesBotKeyComponents.getDeepseekAiKey();
            }
            if (aiKey == null) aiKey = telegramCaloriesBotKeyComponents.getAiKey();

            AiResponse aiResponse = processorHandler.getProvider(provider)
                    .fetchResponsePhotoOrNull(
                            imageUrl,
                            AI_PHOTO_REQUEST,
                            chatModel,
                            aiKey
                    );

            if (aiResponse == null) {
                log.info(chatModel.getName() + " ошибка при генераации ответа");
                continue;
            }

            DishDto dto = readValue(aiResponse.getContent());
            map.put(chatModel, dto);
        }
        return map;
    }

    private Dish getDishByAiResponseOrNull(Long userId, String response) {
        DishDto dto = readValue(response).setUserId(userId);
        return dto.getIsDish() ?
                createDishOrNull(dto) :
                null
                ;
    }

    private DishDto readValue(String value) {
        if (value.startsWith("```")) {
            value = stripJsonFence(value);
        }
        try {
            return objectMapper.readValue(value, DishDto.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации");
            log.info(value);
            return null;
        }
    }

    private static String stripJsonFence(String s) {
        if (s == null) return null;
        s = s.replace("\r", "");
        String lower = s.toLowerCase();
        if (lower.startsWith("```json\n")) {
            // с концом на ``` (с \n перед ним необязателен)
            int end = s.lastIndexOf("```");
            if (end > 0) {
                // убираем ведущий ```json\n и хвостовой ```
                String inner = s.substring("```json\n".length(), end);
                // срежем крайние перевод строки/пробелы
                return inner.strip();
            }
        }
        return s;
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
