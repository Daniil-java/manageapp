package com.kuklin.manageapp.bots.caloriebot.components.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.components.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.components.repository.DishRepository;
import com.kuklin.manageapp.bots.caloriebot.configurations.DishLimitsProperties;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.models.ManualDishRequest;
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
    private final AiInputValidator aiInputValidator;
    private final AiRateLimiter aiRateLimiter;
    private final DishLimitsProperties dishLimits;

    // --- Public Methods ---

    // Методы с вызовом ИИ — без @Transactional: транзакция держала бы соединение с БД, пока ждём ответ (10–30 с).
    // В транзакции только сохранение результата — saveDishes.

    //TODO Исправить на запрос премиума
    public List<DishDto> processPhotoAndGetListDto(Long userId, String photoBase64, String message) {
        String photoDataUrl = aiInputValidator.toPhotoDataUrl(photoBase64, message);
        aiRateLimiter.acquireOrThrow(userId);
        AccessResult<List<Dish>> result = selfProvider.getIfAvailable().getDishDtoByPhoto(userId, photoDataUrl, message);
        try {
            List<Dish> dishes = result.getOrThrow();
            // ИИ не нашёл еду — сообщаем, засчитана ли попытка (тариф с лимитом). Безлимит — как раньше, пустой список.
            switch (result.emptyResultCharge()) {
                case FORGIVEN -> throw new ErrorResponseException(ErrorStatus.PHOTO_NOT_RECOGNIZED_NOT_COUNTED);
                case CHARGED -> throw new ErrorResponseException(ErrorStatus.PHOTO_NOT_RECOGNIZED_COUNTED);
                case NONE -> { }
            }
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


    @RequiresFeature(value = BotFeature.DISH_AI_VISION, botIdentifier = BotIdentifier.CALORIE_BOT,
            forgiveEmptyOncePerDay = true)
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

    public List<DishDto> getDishDtoByDescriptionOrNull(Long userId, String text) {
        aiInputValidator.validateText(text);
        aiRateLimiter.acquireOrThrow(userId);
        return DishDto.fromEntities(getDishByDescriptionOrNull(userId, text));
    }

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
    public DishDto addManualDishDto(Long userId, ManualDishRequest request) {
        checkDailyDishLimitOrThrow(userId);
        // Вес и порции необязательны; без них — те же значения по умолчанию, что и у блюд от ИИ (DishDto.checkValuesNotNull)
        int weight = request.getWeight() != null ? request.getWeight() : 1;
        int portions = request.getPortions() != null ? request.getPortions() : 1;
        Dish dish = new Dish()
                .setUserId(userId)
                .setName(request.getName().trim())
                .setCalories(request.getCalories())
                .setProteins(nvl(request.getProteins()))
                .setFats(nvl(request.getFats()))
                .setCarbohydrates(nvl(request.getCarbohydrates()))
                .setCategory(request.getCategory() != null ? request.getCategory() : Dish.FoodCategory.UNKNOWN)
                .setWeight(weight)
                .setPortions(portions)
                .setPortionWeight(Math.max(1, Math.round((float) weight / portions)))
                .setAiConfidence(0);

        userSettingsService.updateMealLastReminder(userId);
        return DishDto.fromEntity(dishRepository.save(dish));
    }

    /**
     * Суточный лимит на блюда без ИИ (вручную, из избранного): 429 DISH_DAILY_LIMIT.
     * Считаются все блюда за последние 24 часа — живой человек до лимита не дойдёт, а скрипт упрётся.
     */
    public void checkDailyDishLimitOrThrow(Long userId) {
        if (isDailyDishLimitReached(userId)) {
            throw new ErrorResponseException(ErrorStatus.DISH_DAILY_LIMIT, String.format(
                    "Too many dishes added in the last 24 hours (max %d). Try again later.", dishLimits.getPerDay()));
        }
    }

    private boolean isDailyDishLimitReached(Long userId) {
        long added = dishRepository.countByUserIdAndCreatedAfter(userId, Instant.now().minus(Duration.ofDays(1)));
        if (added >= dishLimits.getPerDay()) {
            log.warn("Daily dish limit reached for user {}: {} in 24 hours", userId, added);
            return true;
        }
        return false;
    }

    private static int nvl(Integer value) {
        return value != null ? value : 0;
    }

    @Transactional
    public Dish addDishOrNull(UserFavoriteDish userFavoriteDish) {
        if (userFavoriteDish.getUserId() == null) return null;
        // Бот покажет «Не удалось добавить блюдо»; API проверяет лимит раньше и отвечает 429
        if (isDailyDishLimitReached(userFavoriteDish.getUserId())) return null;
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

    /**
     * Сохраняет блюда, распознанные ИИ. Вызывается уже после ответа ИИ — транзакция короткая.
     */
    @Transactional
    public List<Dish> saveDishes(Long userId, List<Dish> dishes) {
        List<Dish> saved = dishRepository.saveAll(dishes);
        userSettingsService.updateMealLastReminder(userId);
        return saved;
    }

    // --- Private Methods ---

    /**
     * Разбирает ответ ИИ и сохраняет блюда.
     * null — ИИ не нашёл еду или ответил так, что не разобрать (для фото это «на фото нет еды»).
     * Сбой сохранения — не «нет еды»: бросаем DISH_SAVE_FAILED, FeatureAccessAspect вернёт попытку.
     */
    private List<Dish> getDishListByAiResponseOrNull(Long userId, String response) {
        List<Dish> dishes = toDishesOrNull(userId, response);
        if (dishes == null) {
            return null;
        }

        try {
            // Через прокси, иначе @Transactional у saveDishes не сработает
            dishes = selfProvider.getObject().saveDishes(userId, dishes);
        } catch (RuntimeException e) {
            log.error("Failed to save dishes recognized by AI for user {}", userId, e);
            throw new ErrorResponseException(ErrorStatus.DISH_SAVE_FAILED, e);
        }
        return dishes.isEmpty() ? null : dishes;
    }

    /**
     * Ответ ИИ → несохранённые блюда. null — JSON не разобрался, в нём нет блюд или данные кривые.
     */
    private List<Dish> toDishesOrNull(Long userId, String response) {
        List<DishDto> dtos = parseJsonOrNull(response, new TypeReference<List<DishDto>>() {});
        if (dtos == null || dtos.isEmpty()) {
            return null;
        }

        List<Dish> dishes = new ArrayList<>();
        try {
            for (DishDto dto : dtos) {
                dto.checkValuesNotNull();
                if (dto.getIsDish() != null && dto.getIsDish()) {
                    dto.setUserId(userId);
                    dishes.add(Dish.toEntity(dto));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Invalid dish data in AI response for user {}: {}", userId, e.getMessage());
            return null;
        }
        return dishes.isEmpty() ? null : dishes;
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
        AiInputValidator.VoiceInput voice = aiInputValidator.toVoiceInput(base64Audio, format);
        // Одно обращение на голосовое, хотя ИИ вызывается дважды (расшифровка + анализ)
        aiRateLimiter.acquireOrThrow(tgUserId);
        String request = openAiIntegrationService.fetchAudioResponse(
                telegramCaloriesBotKeyComponents.getAiKey(),
                voice.bytes(),
                voice.fileName(),
                voice.mimeType(),
                BotIdentifier.CALORIE_BOT,
                getClass().getSimpleName() + ": processVoice!"
        );

        // Расшифровку не проверяем лимитом текста: длину голоса уже ограничили
        return DishDto.fromEntities(getDishByDescriptionOrNull(tgUserId, request));
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

        Set<LocalDate> activeDays = new HashSet<>();
        for (Instant created : dishRepository.findCreatedByUserIdAndCreatedBetween(userId, start, end)) {
            activeDays.add(created.atZone(zone).toLocalDate());
        }

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