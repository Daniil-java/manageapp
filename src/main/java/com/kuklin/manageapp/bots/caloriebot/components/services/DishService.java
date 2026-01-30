package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessorHandler;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.kuklin.manageapp.bots.caloriebot.entities.Dish.scale;
import static com.kuklin.manageapp.bots.caloriebot.utils.DishCalorieBotPrompts.AI_PHOTO_REQUEST;
import static com.kuklin.manageapp.bots.caloriebot.utils.DishCalorieBotPrompts.AI_REQUEST;

@Service
@RequiredArgsConstructor
@Slf4j
public class DishService {
    private final DishRepository dishRepository;
    private final OpenAiProviderProcessor openAiIntegrationService;
    private final TelegramCaloriesBotKeyComponents telegramCaloriesBotKeyComponents;
    private final ObjectMapper objectMapper;
    private final ProviderProcessorHandler processorHandler;
    private final UserSettingsService userSettingsService;

    // --- Public Methods ---

    @Transactional
    @RequiresFeature(value = BotFeature.DISH_AI_VISION, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<List<Dish>> getDishDtoByPhoto(Long userId, String imageUrl, String message) {
        String aiPhotoPrompt = String.format(AI_PHOTO_REQUEST, message);
        String aiResponse = openAiIntegrationService.fetchPhotoResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                aiPhotoPrompt,
                imageUrl,
                BotIdentifier.CALORIE_BOT
        );
        return AccessResult.success(getDishListByAiResponseOrNull(userId, aiResponse));
    }

    @Transactional
    public List<Dish> getDishByDescriptionOrNull(Long userId, String text) {
        String aiResponse = openAiIntegrationService.fetchResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                String.format(AI_REQUEST, text),
                CalorieTelegramBot.BOT_IDENTIFIER,
                this.getClass().getSimpleName(),
                MetricsAiInteractionRecord.AiMessageType.TEXT
        );
        return getDishListByAiResponseOrNull(userId, aiResponse);
    }

    @Transactional
    public void removeByDishId(Long id) {
        dishRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Dish> getTodayDishes(Long userId) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        ZonedDateTime userNow = ZonedDateTime.now(userZone);
        ZonedDateTime startOfDayUser = userNow.toLocalDate().atStartOfDay(userZone);
        ZonedDateTime endOfDayUser = startOfDayUser.plusDays(1);

        return dishRepository.findAllByUserIdAndCreatedBetween(
                userId,
                startOfDayUser.toInstant(),
                endOfDayUser.toInstant()
        );
    }

    @Transactional(readOnly = true)
    public List<Dish> getWeekDishes(Long userId) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();
        ZonedDateTime userNow = ZonedDateTime.now(userZone);

        ZonedDateTime start = userNow.toLocalDate().minusDays(7).atStartOfDay(userZone);
        ZonedDateTime end = userNow;

        return dishRepository.findAllByUserIdAndCreatedBetween(
                userId,
                start.toInstant(),
                end.toInstant()
        );
    }

    @Transactional(readOnly = true)
    public Dish getDishByIdOrNull(Long dishId) {
        return dishRepository.findById(dishId).orElse(null);
    }

    @Transactional
    public Dish addDishOrNull(UserFavoriteDish userFavoriteDish) {
        if (userFavoriteDish.getUserId() == null) return null;
        Dish dish = UserFavoriteDish.toDish(userFavoriteDish);
        userSettingsService.updateMealLastReminder(dish.getUserId());
        return dishRepository.save(dish);
    }

    @Transactional
    public Dish changePortionWeightByPercent(Long dishId, int percentDelta) {
        return dishRepository.findById(dishId)
                .map(dish -> dish.applyPortionWeightPercentDelta(percentDelta))
                .map(dishRepository::save)
                .orElse(null);
    }

    @Transactional
    public Dish saveNewPortionsCountOrNull(Long dishId, Integer newPortions) {
        if (newPortions == null || newPortions <= 0) {
            return null;
        }

        Dish dish = getDishByIdOrNull(dishId);
        if (dish == null) {
            return null;
        }

        Integer oldPortions = dish.getPortions();

        if (oldPortions == null || oldPortions <= 0) {
            dish.setPortions(newPortions);

            if (dish.getPortionWeight() != null) {
                dish.setWeightGrams(dish.getPortionWeight() * newPortions);
            }

            return dishRepository.save(dish);
        }

        double multiplier = newPortions / (double) oldPortions;

        dish.setPortions(newPortions);

        if (dish.getPortionWeight() != null) {
            dish.setWeightGrams(dish.getPortionWeight() * newPortions);
        } else if (dish.getWeightGrams() != null) {
            dish.setWeightGrams(scale(dish.getWeightGrams(), multiplier));
        }

        dish.setCalories(scale(dish.getCalories(), multiplier));
        dish.setProteins(scale(dish.getProteins(), multiplier));
        dish.setFats(scale(dish.getFats(), multiplier));
        dish.setCarbohydrates(scale(dish.getCarbohydrates(), multiplier));

        return dishRepository.save(dish);
    }

    @Transactional(readOnly = true)
    public List<Dish> getAllDishedByUserIdAndPeriod(Long userId, Instant from, Instant to) {
        return dishRepository.findAllByUserIdAndCreatedBetween(userId, from, to);
    }

    // --- Private Methods ---

    private List<Dish> getDishListByAiResponseOrNull(Long userId, String response) {
        try {
            List<DishDto> dtos = parseJsonOrNull(response, new TypeReference<List<DishDto>>() {});

            if (dtos == null || dtos.isEmpty()) {
                return null;
            }

            List<Dish> dishes = new ArrayList<>();
            for (DishDto dto : dtos) {
                if (dto.getIsDish() != null && dto.getIsDish()) {
                    dto.setUserId(userId);
                    Dish dish = Dish.toEntity(dto);
                    dishes.add(dish);
                }
            }

            dishes = dishRepository.saveAll(dishes);
            userSettingsService.updateMealLastReminder(userId);

            return dishes.isEmpty() ? null : dishes;

        } catch (Exception e) {
            return null;
        }
    }

    private <T> T parseJsonOrNull(String json, TypeReference<T> typeReference) {
        if (json == null) return null;

        if (json.startsWith("```")) {
            json = stripJsonFence(json);
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("JSON deserialization error: {}", e.getMessage());
            return null;
        }
    }

    private static String stripJsonFence(String s) {
        if (s == null) return null;
        s = s.replace("\r", "");
        String lower = s.toLowerCase();
        if (lower.startsWith("```json\n")) {
            int end = s.lastIndexOf("```");
            if (end > 0) {
                String inner = s.substring("```json\n".length(), end);
                return inner.strip();
            }
        }
        return s;
    }

    // --- Commented Methods ---

    //Не работает, если несколько блюд вернется. Требует дополнительного рефакторинга, для обработки списка блюд
//        @Deprecated
//    @Transactional
//    public Map<ChatModel, DishDto> getDishDtoByPhotoOrNullWithManyProviders(String imageUrl, String message) {
//        Map<ChatModel, DishDto> map = new EnumMap<>(ChatModel.class);
//        for (ChatModel chatModel : ChatModel.getModels()) {
//            ProviderVariant provider = chatModel.getProviderVariant();
//
//            String aiKey = null;
//            switch (chatModel.getProviderVariant()) {
//                case CLAUDE -> aiKey = telegramCaloriesBotKeyComponents.getClaudeAiKey();
//                case GEMINI -> aiKey = telegramCaloriesBotKeyComponents.getGeminiAiKey();
//                case DEEPSEEK -> aiKey = telegramCaloriesBotKeyComponents.getDeepseekAiKey();
//            }
//            if (aiKey == null) aiKey = telegramCaloriesBotKeyComponents.getAiKey();
//
//            AiResponse aiResponse = processorHandler.getProvider(provider)
//                    .fetchResponsePhotoOrNull(
//                            imageUrl,
//                            String.format(AI_PHOTO_REQUEST, message),
//                            chatModel,
//                            aiKey,
//                            CalorieTelegramBot.BOT_IDENTIFIER,
//                            this.getClass().getSimpleName()
//                    );
//
//            if (aiResponse == null) {
//                log.info(chatModel.getName() + " ошибка при генераации ответа");
//                continue;
//            }
//
//            List<DishDto> dishes = parseJsonOrNull(aiResponse.getContent(), new TypeReference<List<DishDto>>() {});
//            if (dishes != null && !dishes.isEmpty()) {
//                map.put(chatModel, dishes.get(0));
//            }
//        }
//        return map;
//    }
}