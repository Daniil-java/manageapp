package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private final UserSettingsService userSettingsService;
    private final CalorieAccessService calorieAccessService;
    private final TelegramUserService telegramUserService;
    private final ObjectProvider<DishService> selfProvider;

    // --- Public Methods ---

    //TODO Исправить на запрос премиума
    @Transactional
    public List<DishDto> processPhotoAndGetListDto(Long userId, String photoBase64, String message) {
        AccessResult<List<Dish>> result = selfProvider.getIfAvailable().getDishDtoByPhoto(userId, "data:image/jpeg;base64," + photoBase64, message);
        try {
            List<Dish> dishes = result.getOrThrow();
            Optional<TelegramUser> optUser = telegramUserService.findByAppUserIdAndBotIdentifier(userId, BotIdentifier.CALORIE_BOT);
            if (optUser.isEmpty()) {
                throw new ErrorResponseException(ErrorStatus.USER_NOT_FOUND);
            }
            calorieAccessService.incrementResponses(optUser.get());
            return DishDto.fromEntities(dishes);
        } catch (MissingFeatureException e) {
            throw new ErrorResponseException(ErrorStatus.MISSING_FEATURE);
        }
    }


    @Transactional
    @RequiresFeature(value = BotFeature.DISH_AI_VISION, botIdentifier = BotIdentifier.CALORIE_BOT)
    public AccessResult<List<Dish>> getDishDtoByPhoto(Long userId, String photoBase64, String message) {
        String aiPhotoPrompt = String.format(AI_PHOTO_REQUEST, message);
        String aiResponse = openAiIntegrationService.fetchPhotoResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                aiPhotoPrompt,
                photoBase64,
                BotIdentifier.CALORIE_BOT
        );
        return AccessResult.success(getDishListByAiResponseOrNull(userId, aiResponse));
    }

    @Transactional
    public List<DishDto> getDishDtoByDescriptionOrNull(Long userId, String text) {
        return DishDto.fromEntities(getDishByDescriptionOrNull(userId, text));
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

    @Transactional
    public void removeByDishId(Long appUserId, Long id) {
        Dish dish = dishRepository.findById(id).orElseThrow(
                () -> new ErrorResponseException(ErrorStatus.DISH_NOT_FOUND));
        if (!dish.getUserId().equals(appUserId)) {
            throw new ErrorResponseException(ErrorStatus.DISH_NOT_BELONG_USER);
        }
        dishRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DishDto> getTodayDishesDto(Long userId) {
        List<Dish> dishes = getTodayDishes(userId);

        return DishDto.fromEntities(dishes);
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
                dish.setWeight(dish.getPortionWeight() * newPortions);
            }

            return dishRepository.save(dish);
        }

        double multiplier = newPortions / (double) oldPortions;

        dish.setPortions(newPortions);

        if (dish.getPortionWeight() != null) {
            dish.setWeight(dish.getPortionWeight() * newPortions);
        } else if (dish.getWeight() != null) {
            dish.setWeight(scale(dish.getWeight(), multiplier));
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
            List<com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto> dtos = parseJsonOrNull(response, new TypeReference<List<com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto>>() {});

            if (dtos == null || dtos.isEmpty()) {
                return null;
            }

            for (com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto dto: dtos) {
                dto.checkValuesNotNull();
            }

            List<Dish> dishes = new ArrayList<>();
            for (com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto dto : dtos) {
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

    public DishDto updateDishPortion(Long userId, Long dishId, DishDto request) {
        Dish dish = getDishByIdOrNull(dishId);
        if (!dish.getUserId().equals(userId)) {
            //TODO выкидывать ошибку
            return null;
        }

        dish = request.mergeToEntity(dish);
        dish = dishRepository.save(dish);
        return DishDto.fromEntity(dish);

    }

    @Transactional(readOnly = true)
    public List<DishDto> getDishesByPeriodDto(Long userId, LocalDate from, LocalDate to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        // Выдаст ровно "2026-04-29T00:00:00"
        String fromStr = from.atStartOfDay().format(formatter);

        // Выдаст ровно "2026-05-28T23:59:59"
        String toStr = to.atTime(23, 59, 59).format(formatter);

        List<Dish> dishes = getDishesByPeriod(userId, fromStr, toStr);
        return DishDto.fromEntities(dishes);
    }

    @Transactional(readOnly = true)
    public List<Dish> getDishesByPeriod(Long userId, String from, String to) {
        return getDishesByPeriod(
                userId,
                LocalDateTime.parse(from),
                LocalDateTime.parse(to)
        );
    }

    @Transactional(readOnly = true)
    public List<Dish> getDishesByPeriod(Long userId, LocalDateTime from, LocalDateTime to) {
        ZoneId userZone = userSettingsService.getOrCreate(userId).getZoneId();

        return dishRepository.findAllByUserIdAndCreatedBetween(
                userId,
                from.atZone(userZone).toInstant(),
                to.atZone(userZone).toInstant()
        );
    }

    @Transactional(readOnly = true)
    public List<Dish> getDishesByPeriod(Long userId, LocalDate from, LocalDate to) {
        return getDishesByPeriod(
                userId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay().minusNanos(1)
        );
    }
    public List<DishDto> processVoiceAndGetListDto(Long tgUserId, String base64Audio, String format) {
        String request = openAiIntegrationService.fetchAudioResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                Base64.getDecoder().decode(base64Audio),
                BotIdentifier.CALORIE_BOT,
                getClass().getSimpleName() + ": processVoice!"
        );

        return getDishDtoByDescriptionOrNull(tgUserId, request);
    }

    @Transactional(readOnly = true)
    public int getCurrentStreak(Long userId) {

        ZoneId zone = userSettingsService.getOrCreate(userId).getZoneId();

        LocalDate today = LocalDate.now(zone);

        Instant start = today.minusYears(1)
                .atStartOfDay(zone)
                .toInstant();

        Instant end = today.plusDays(1)
                .atStartOfDay(zone)
                .toInstant();

        Set<LocalDate> activeDays = new HashSet<>(
                dishRepository.findDistinctDaysByUserIdAndCreatedBetween(
                        userId,
                        start,
                        end
                )
        );

        LocalDate currentDay = activeDays.contains(today)
                ? today
                : today.minusDays(1);

        int streak = 0;

        while (activeDays.contains(currentDay)) {
            streak++;
            currentDay = currentDay.minusDays(1);
        }

        return streak;
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