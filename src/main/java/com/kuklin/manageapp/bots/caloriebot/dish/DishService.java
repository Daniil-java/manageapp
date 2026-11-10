package com.kuklin.manageapp.bots.caloriebot.dish;
import com.kuklin.manageapp.aiconversation.models.enums.AiMessageType;
import com.kuklin.manageapp.bots.caloriebot.settings.UserSettingsService;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.AiDishResponse;
import com.kuklin.manageapp.bots.caloriebot.dish.ai.AiInputValidator;
import com.kuklin.manageapp.bots.caloriebot.access.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.access.AiRateLimiter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.models.AiUsageContext;
import com.kuklin.manageapp.aiconversation.models.enums.AiOperation;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.access.RequiresFeature;
import com.kuklin.manageapp.bots.caloriebot.bot.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.favorite.UserFavoriteDish;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTexts;
import com.kuklin.manageapp.bots.caloriebot.dish.dto.ManualDishRequest;
import com.kuklin.manageapp.bots.caloriebot.dish.dto.DishDto;
import com.kuklin.manageapp.common.web.error.ErrorParams;
import com.kuklin.manageapp.common.web.error.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.web.error.ErrorStatus;
import com.kuklin.manageapp.common.web.error.CommonErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.access.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.access.model.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.access.model.BotFeature;
import com.kuklin.manageapp.bots.caloriebot.bot.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.bots.caloriebot.i18n.AppLanguage;
import com.kuklin.manageapp.common.telegram.BotIdentifier;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.kuklin.manageapp.bots.caloriebot.dish.Dish.scale;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.AI_VOICE_TRANSCRIPTION_PROMPT;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.PHOTO_INSTRUCTIONS;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.PHOTO_NAME_LANGUAGE_FROM_CAPTION;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.PHOTO_NAME_LANGUAGE_NO_CAPTION;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.PHOTO_NO_CAPTION;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.PHOTO_USER_MESSAGE;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.TEXT_INSTRUCTIONS;
import static com.kuklin.manageapp.bots.caloriebot.dish.ai.DishCalorieBotPrompts.TEXT_USER_MESSAGE;

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
    private final CalorieTexts calorieTexts;
    private final ApplicationEventPublisher eventPublisher;

    // --- Public Methods ---

    /** Дни (в таймзоне zone), в которые есть блюда — для серии дней (StreakService). */
    @Transactional(readOnly = true)
    public List<LocalDate> findDishDays(Long userId, ZoneId zone) {
        return dishRepository.findDishEpochDaysByUserId(userId, getSqlZoneName(zone), getSqlOffsetSeconds(zone))
                .stream()
                .map(LocalDate::ofEpochDay)
                .toList();
    }

    /** Сколько разных дней (в таймзоне zone) есть блюда за [from, to). */
    @Transactional(readOnly = true)
    public long countDishDaysBetween(Long userId, ZoneId zone, Instant from, Instant to) {
        return dishRepository.countDishDaysByUserIdBetween(userId, getSqlZoneName(zone), getSqlOffsetSeconds(zone),
                from, to);
    }

    // Фиксированное смещение (+03:00, GMT+3) — числом поверх UTC, зона с переходами — по имени IANA
    private static String getSqlZoneName(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset ? "UTC" : zone.getId();
    }

    private static int getSqlOffsetSeconds(ZoneId zone) {
        return zone.normalized() instanceof ZoneOffset offset ? offset.getTotalSeconds() : 0;
    }

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
                throw new ErrorResponseException(CommonErrorStatus.USER_NOT_FOUND);
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
        boolean hasCaption = message != null && !message.isBlank();
        String nameLanguageRule = String.format(
                hasCaption ? PHOTO_NAME_LANGUAGE_FROM_CAPTION : PHOTO_NAME_LANGUAGE_NO_CAPTION, aiLanguageOf(userId));
        String userMessage = String.format(PHOTO_USER_MESSAGE, getQuotedCaption(message), nameLanguageRule);
        String aiResponse;
        try (var ignored = AiUsageContext.open(userId, AiOperation.DISH_PHOTO)) {
            aiResponse = openAiIntegrationService.fetchStructuredResponse(
                    telegramCaloriesBotKeyComponents.getAiKey(),
                    PHOTO_INSTRUCTIONS,
                    userMessage,
                    photoBase64,
                    AiDishResponse.RESPONSE_FORMAT,
                    BotIdentifier.CALORIE_BOT,
                    AiMessageType.PHOTO
            );
        }
        return AccessResult.success(getDishListByAiResponseOrNull(userId, aiResponse));
    }

    /**
     * Распознать еду на фото для канала («Угадай калорийность»): тот же промпт, что у пользователей,
     * но без пользователя, лимитов и сохранения в дневник. Названия — по-русски.
     *
     * @param imageUrl публичная ссылка на фото
     * @param hint     название блюда — как подпись пользователя к фото
     * @return блюда (только съедобное) или null, если ИИ ничего не нашёл или ответ не разобрался
     */
    public List<DishDto> recognizePhotoForChannelOrNull(String imageUrl, String hint) {
        String nameLanguageRule = String.format(PHOTO_NAME_LANGUAGE_FROM_CAPTION, AppLanguage.RU.getNativeName());
        String aiResponse;
        try (var ignored = AiUsageContext.open(null, AiOperation.CHANNEL_PHOTO)) {
            aiResponse = openAiIntegrationService.fetchStructuredResponse(
                    telegramCaloriesBotKeyComponents.getAiKey(),
                    PHOTO_INSTRUCTIONS,
                    String.format(PHOTO_USER_MESSAGE, getQuotedCaption(hint), nameLanguageRule),
                    imageUrl,
                    AiDishResponse.RESPONSE_FORMAT,
                    BotIdentifier.CHANNEL_POSTER,
                    AiMessageType.PHOTO
            );
        }
        return findDishDtosOrNull(aiResponse);
    }

    /** Подпись к фото для промпта: в кавычках, без подписи — «нет». */
    private static String getQuotedCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            return PHOTO_NO_CAPTION;
        }
        return "«" + caption + "»";
    }

    public List<DishDto> getDishDtoByDescriptionOrNull(Long userId, String text) {
        aiInputValidator.validateText(text);
        aiRateLimiter.acquireOrThrow(userId);
        return DishDto.fromEntities(getDishByDescriptionOrNull(userId, text));
    }

    public List<Dish> getDishByDescriptionOrNull(Long userId, String text) {
        String aiResponse;
        // Из голосового — уже открыт DISH_VOICE, он и запишется
        try (var ignored = AiUsageContext.open(userId, AiOperation.DISH_TEXT)) {
            aiResponse = openAiIntegrationService.fetchStructuredResponse(
                    telegramCaloriesBotKeyComponents.getAiKey(),
                    TEXT_INSTRUCTIONS,
                    String.format(TEXT_USER_MESSAGE, text, aiLanguageOf(userId)),
                    null,
                    AiDishResponse.RESPONSE_FORMAT,
                    CalorieTelegramBot.BOT_IDENTIFIER,
                    AiMessageType.TEXT
            );
        }
        return getDishListByAiResponseOrNull(userId, aiResponse);
    }

    /** Язык пользователя для промпта: на нём ИИ называет блюда, если по описанию язык не понять (или описания нет). */
    private String aiLanguageOf(Long userId) {
        return calorieTexts.languageOf(userId).getNativeName();
    }

    /**
     * Удалить блюдо из дневника (бот, кнопка «Удалить»). false — блюда нет или оно чужое:
     * id приходит в callback_data, а её клиент может подделать.
     */
    @Transactional
    public boolean removeUserDish(Long userId, Long dishId) {
        Optional<Dish> dish = dishRepository.findByIdAndUserId(dishId, userId);
        dish.ifPresent(dishRepository::delete);
        return dish.isPresent();
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
        ZoneId userZone = userSettingsService.getZoneId(userId);
        return getDishesForDate(userId, LocalDate.now(userZone));
    }

    /** Блюда за календарный день пользователя (по его таймзоне) — экран «Сегодня» листает прошлые дни. */
    @Transactional(readOnly = true)
    public List<Dish> getDishesForDate(Long userId, LocalDate date) {
        ZoneId userZone = userSettingsService.getZoneId(userId);

        ZonedDateTime startOfDayUser = date.atStartOfDay(userZone);
        ZonedDateTime endOfDayUser = startOfDayUser.plusDays(1);

        return dishRepository.findAllByUserIdAndCreatedBetween(
                userId,
                startOfDayUser.toInstant(),
                endOfDayUser.toInstant()
        );
    }

    /** Когда пользователь последний раз добавил блюдо; null — ни разу. */
    @Transactional(readOnly = true)
    public Instant getLastDishTimeOrNull(Long userId) {
        return dishRepository.findLastCreatedByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Dish> getWeekDishes(Long userId) {
        ZoneId userZone = userSettingsService.getZoneId(userId);
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

    /** Блюдо пользователя или null, если его нет или оно чужое. Для бота: id блюда приходит в callback_data. */
    @Transactional(readOnly = true)
    public Dish getUserDishOrNull(Long userId, Long dishId) {
        return dishRepository.findByIdAndUserId(dishId, userId).orElse(null);
    }

    @Transactional
    public DishDto addManualDishDto(Long userId, ManualDishRequest request) {
        checkDailyDishLimitOrThrow(userId);
        // Вес и порции необязательны; без них — те же значения по умолчанию, что и у блюд от ИИ (AiDishResponse.AiDish.toDishDto)
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

        markDishAdded(userId);
        return DishDto.fromEntity(dishRepository.save(dish));
    }

    /**
     * Суточный лимит на блюда без ИИ (вручную, из избранного): 429 DISH_DAILY_LIMIT.
     * Считаются все блюда за последние 24 часа — живой человек до лимита не дойдёт, а скрипт упрётся.
     */
    public void checkDailyDishLimitOrThrow(Long userId) {
        if (isDailyDishLimitReached(userId)) {
            throw new ErrorResponseException(ErrorStatus.DISH_DAILY_LIMIT, ErrorParams.max(dishLimits.getPerDay()));
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

    /**
     * Блюдо добавлено в дневник: сдвинуть напоминание о еде и сообщить слушателям (награда за приглашение —
     * ReferralRewardNotifier). Все пути добавления блюд — ИИ, вручную, из избранного — идут через этот метод.
     */
    private void markDishAdded(Long userId) {
        userSettingsService.updateMealLastReminder(userId);
        eventPublisher.publishEvent(new DishAddedEvent(userId));
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
        markDishAdded(dish.getUserId());
        return dishRepository.save(dish);
    }

    /**
     * Изменить вес порции на percentDelta процентов (бот, кнопки ±10%).
     * null — блюда нет или оно чужое: id приходит в callback_data, а её клиент может подделать.
     * Процент не ограничиваем — подделать его можно только для своего блюда; ниже нуля вес не уйдёт.
     */
    @Transactional
    public Dish changePortionWeightByPercentOrNull(Long userId, Long dishId, int percentDelta) {
        return dishRepository.findByIdAndUserId(dishId, userId)
                .map(dish -> dish.applyPortionWeightPercentDelta(percentDelta))
                .map(dishRepository::save)
                .orElse(null);
    }

    /**
     * Новое количество порций (бот, кнопки ➖ ➕). null — блюда нет, оно чужое или порций меньше одной.
     */
    @Transactional
    public Dish saveNewPortionsCountOrNull(Long userId, Long dishId, Integer newPortions) {
        if (newPortions == null || newPortions <= 0) {
            return null;
        }

        Dish dish = getUserDishOrNull(userId, dishId);
        if (dish == null) {
            return null;
        }

        Integer oldPortions = dish.getPortions();

        if (oldPortions == null || oldPortions <= 0) {
            dish.setPortions(newPortions);

            if (dish.getPortionWeight() != null) {
                dish.setWeight(scale(dish.getPortionWeight(), newPortions));
            }

            return dishRepository.save(dish);
        }

        double multiplier = newPortions / (double) oldPortions;

        dish.setPortions(newPortions);

        if (dish.getPortionWeight() != null) {
            dish.setWeight(scale(dish.getPortionWeight(), newPortions));
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
        markDishAdded(userId);
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
     * Ответ ИИ → несохранённые блюда. null — JSON не разобрался или в нём нет блюд.
     */
    private List<Dish> toDishesOrNull(Long userId, String response) {
        List<DishDto> dtos = findDishDtosOrNull(response);
        if (dtos == null) {
            return null;
        }

        List<Dish> dishes = new ArrayList<>();
        for (DishDto dto : dtos) {
            dto.setUserId(userId);
            dishes.add(Dish.toEntity(dto));
        }
        return dishes;
    }

    /**
     * Ответ ИИ по схеме {@link AiDishResponse} → блюда с пищевой ценностью на всю порцию.
     * null — JSON не разобрался или в нём нет блюд (блюда без названия отбрасываем).
     */
    private List<DishDto> findDishDtosOrNull(String response) {
        if (response == null) {
            return null;
        }
        AiDishResponse aiResponse;
        try {
            aiResponse = objectMapper.readValue(response, AiDishResponse.class);
        } catch (JsonProcessingException e) {
            log.error("JSON deserialization error: {}", e.getMessage());
            return null;
        }
        if (aiResponse.dishes() == null) {
            return null;
        }

        List<DishDto> dishes = new ArrayList<>();
        for (AiDishResponse.AiDish aiDish : aiResponse.dishes()) {
            if (aiDish == null || aiDish.name() == null || aiDish.name().isBlank()) {
                continue;
            }
            dishes.add(aiDish.toDishDto());
        }
        return dishes.isEmpty() ? null : dishes;
    }

    @Transactional
    public DishDto updateDishPortion(Long userId, Long dishId, DishDto request) {
        Dish dish = dishRepository.findById(dishId).orElseThrow(
                () -> new ErrorResponseException(ErrorStatus.DISH_NOT_FOUND));
        if (!dish.getUserId().equals(userId)) {
            throw new ErrorResponseException(ErrorStatus.DISH_NOT_BELONG_USER);
        }

        return DishDto.fromEntity(dishRepository.save(request.mergeToEntity(dish)));
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
        ZoneId userZone = userSettingsService.getZoneId(userId);

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
        try (var ignored = AiUsageContext.open(tgUserId, AiOperation.DISH_VOICE)) {
            String request = openAiIntegrationService.fetchAudioResponse(
                    telegramCaloriesBotKeyComponents.getAiKey(),
                    voice.bytes(),
                    voice.fileName(),
                    voice.mimeType(),
                    AI_VOICE_TRANSCRIPTION_PROMPT,
                    BotIdentifier.CALORIE_BOT,
                    getClass().getSimpleName() + ": processVoice!"
            );

            // Расшифровку не проверяем лимитом текста: длину голоса уже ограничили
            return DishDto.fromEntities(getDishByDescriptionOrNull(tgUserId, request));
        }
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
