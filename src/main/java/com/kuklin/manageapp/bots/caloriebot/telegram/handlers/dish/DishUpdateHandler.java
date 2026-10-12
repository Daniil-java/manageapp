package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.caloriebot.components.services.AiInputValidator;
import com.kuklin.manageapp.bots.caloriebot.components.services.AiRateLimiter;
import com.kuklin.manageapp.bots.caloriebot.components.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.components.services.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishChoiceChatModelService;
import com.kuklin.manageapp.bots.caloriebot.components.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.i18n.BotTextKeys;
import com.kuklin.manageapp.bots.caloriebot.i18n.CalorieTextsService;
import com.kuklin.manageapp.bots.caloriebot.models.entitydtos.DishDto;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorResponseException;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.ErrorStatus;
import com.kuklin.manageapp.bots.caloriebot.models.exceptions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.models.feature.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.models.feature.EmptyResultCharge;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.FeatureLimitNotifier;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieMenuButtons;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.common.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.common.library.tgutils.ThreadUtil;
import com.kuklin.manageapp.common.services.TelegramService;
import com.kuklin.manageapp.payment.handlers.PaymentPlanListUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

import static com.kuklin.manageapp.bots.caloriebot.utils.DishCalorieBotPrompts.AI_VOICE_TRANSCRIPTION_PROMPT;

@Component
@RequiredArgsConstructor
@Slf4j
public class DishUpdateHandler implements CalorieBotUpdateHandler {
    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramService telegramService;
    private final TelegramCaloriesBotKeyComponents caloriesBotKeyComponents;
    private final DishService dishService;
    private final DishChoiceChatModelService dishChoiceChatModelService;
    private final PaymentPlanListUpdateHandler paymentPlanListUpdateHandler;
    private final CalorieAccessService calorieAccessService;
    private final AnalyticsService analyticsService;
    private final FeatureLimitNotifier featureLimitNotifier;
    private final AiInputValidator aiInputValidator;
    private final AiRateLimiter aiRateLimiter;
    private final CalorieTextsService texts;
    private final CalorieMenuButtons menuButtons;
    private final DishKeyboards dishKeyboards;

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        List<Dish> dishes;
        try {
            dishes = getDishOrNull(update, telegramUser);
        } catch (ErrorResponseException e) {
            // ИИ распознал блюда, но в БД они не записались — это не «не съедобно», говорим как есть
            if (e.getErrorStatus() != ErrorStatus.DISH_SAVE_FAILED) throw e;
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    texts.error(telegramUser.getAppUserId(), e),
                    menuButtons.buildKeyboard(telegramUser.getAppUserId()),
                    null
            );
            return;
        }
        if (dishes == null || dishes.isEmpty()) return;

        for (Dish dish: dishes) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    analyticsService.getInfo(dish, telegramUser.getAppUserId()),
                    dishKeyboards.buildPortionKeyboard(dish, telegramUser.getAppUserId()),
                    null
            );
            ThreadUtil.sleep(100);
        }
    }

    /**
     * Обрабатывает апдейт и пытается получить Dish.
     * Возвращает:
     * - Dish, если всё прошло успешно;
     * - null, если были ошибки (при этом в большинстве случаев уже отправлены сообщения пользователю).
     */
    private List<Dish> getDishOrNull(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getAppUserId();
        List<Dish> dishes;

        // ==== ВЕТКА 1: пользователь прислал фото ====
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            if (!tryAcquireAi(userId, update.getMessage().getChatId())) return null;
            AccessResult<List<Dish>> photoResult;
            try {
                photoResult = processPhotoOrNull(telegramUser, update.getMessage());
                if (photoResult == null) return null; // фото не скачалось — сообщение уже отправлено
                dishes = photoResult.getOrThrow();
            } catch (MissingFeatureException e) {
                featureLimitNotifier.sendLimitReached(update.getMessage().getChatId(), e.getRequiredFeature());
                return null;
            }
            // ИИ не нашёл еду на тарифе с лимитом — объясняем, засчитана ли попытка
            if (photoResult.emptyResultCharge() != EmptyResultCharge.NONE) {
                calorieTelegramBot.sendReturnedMessage(
                        update.getMessage().getChatId(),
                        texts.get(userId, photoResult.emptyResultCharge() == EmptyResultCharge.FORGIVEN
                                ? BotTextKeys.DISH_PHOTO_NOT_RECOGNIZED_NOT_COUNTED
                                : BotTextKeys.DISH_PHOTO_NOT_RECOGNIZED_COUNTED),
                        menuButtons.buildKeyboard(userId),
                        null
                );
                return null;
            }
            // ==== ВЕТКА 2: пользователь прислал голосовое ====
        } else if (update.hasMessage() && update.getMessage().hasVoice()) {
            String request = processVoiceMessageOrNull(userId, update.getMessage());
            // Если не смогли распознать голос — дальше не идём
            if (request == null) {
                return null;
            }
            dishes = processTextOrNull(userId, request, update.getMessage().getChatId());
            // ==== ВЕТКА 3: всё остальное считаем текстовым сообщением ====
        } else {
            String text = update.getMessage().getText();
            if (text != null && !tryAcquireAi(userId, update.getMessage().getChatId())) return null;
            dishes = processTextOrNull(userId, text, update.getMessage().getChatId());
        }

        if (dishes == null || dishes.isEmpty()) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    texts.get(userId, BotTextKeys.DISH_NOT_FOOD),
                    menuButtons.buildKeyboard(userId),
                    null
            );
            return null;
        }
        return dishes;
    }

    private String getDishDtoListString(Map<ChatModel, DishDto> map) {
        if (map == null || map.isEmpty()) return "";
        StringJoiner sj = new StringJoiner(System.lineSeparator());

        for (Entry<ChatModel, DishDto> entry : map.entrySet()) {
            ChatModel model = entry.getKey();
            DishDto dto = entry.getValue();
            if (dto != null) {
                String name = (model != null ? model.getName() : "UNKNOWN_MODEL");
                sj.add("<b>" + name + "</b>\n" + dto.toStringSpecial()); // dto.toString()
                sj.add("\n");
            }
        }

        return sj.toString();
    }

    private List<Dish> processTextOrNull(Long userId, String message, long chatId) {
        if (message == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(userId, BotTextKeys.DISH_UNSUPPORTED_MESSAGE));
            return null;
        }
        return dishService.getDishByDescriptionOrNull(userId, message);
    }

    /**
     * Засчитывает обращение к ИИ. Если лимит исчерпан — отправляет пользователю сообщение и возвращает false.
     */
    private boolean tryAcquireAi(Long userId, Long chatId) {
        AiRateLimiter.Decision decision = aiRateLimiter.tryAcquire(userId);
        if (!decision.allowed()) {
            featureLimitNotifier.sendAiRateLimited(chatId, decision);
        }
        return decision.allowed();
    }

    private String processVoiceMessageOrNull(Long userId, Message message) {
        Long chatId = message.getChatId();
        // Длительность сообщает Telegram — проверяем до скачивания и до вызова ИИ
        if (!aiInputValidator.isVoiceDurationAllowed(message.getVoice().getDuration())) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(userId, BotTextKeys.DISH_VOICE_TOO_LONG,
                    getDurationText(userId, aiInputValidator.getVoiceMaxDuration())));
            return null;
        }
        // Одно обращение на голосовое, хотя ИИ вызывается дважды (расшифровка + анализ)
        if (!tryAcquireAi(userId, chatId)) return null;
        String request = telegramService.convertVoiceToTextOrNull(
                calorieTelegramBot,
                caloriesBotKeyComponents.getAiKey(),
                message,
                AI_VOICE_TRANSCRIPTION_PROMPT);

        if (request == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, texts.get(userId, BotTextKeys.DISH_VOICE_ERROR));
            return null;
        }

        log.info(request);
        return request;
    }

    /** «2 мин» / «90 сек»: целые минуты — минутами, иначе секундами. */
    private String getDurationText(Long userId, Duration duration) {
        long seconds = duration.toSeconds();
        return seconds % 60 == 0
                ? texts.get(userId, BotTextKeys.COMMON_DURATION_MINUTES, String.valueOf(seconds / 60))
                : texts.get(userId, BotTextKeys.COMMON_DURATION_SECONDS, String.valueOf(seconds));
    }

    /**
     * Распознаёт блюда на фото. Возвращает null, если фото не удалось скачать (сообщение уже отправлено).
     */
    private AccessResult<List<Dish>> processPhotoOrNull(TelegramUser telegramUser, Message message) {
        try {
            String photoBase64 = telegramService.downloadPhotoFileBase64OrNull(calorieTelegramBot, message);
            AccessResult<List<Dish>> dishResult = dishService.getDishDtoByPhoto(
                    telegramUser.getAppUserId(), photoBase64, message.getCaption());
            if (dishResult.isAllowed()) {
                calorieAccessService.incrementResponses(telegramUser);
            }
//        processManyAiModels(dish.getId(), photoBase64, message);

            return dishResult;
        } catch (IOException e) {
            log.error("Provider error!");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(),
                    texts.get(telegramUser.getAppUserId(), BotTextKeys.DISH_PHOTO_PROVIDER_ERROR));
            return null;
        }
    }

//    private void processManyAiModels(Long dishId, String photoBase64, Message message) {
//        Map<ChatModel, DishDto> dishDtos;
//        try {
//            dishDtos = dishService.getDishDtoByPhotoOrNullWithManyProviders(photoBase64, message.getCaption());
//        } catch (Exception e) {
//            log.error("Many providers request error!", e);
//            calorieTelegramBot.sendReturnedMessage(
//                    message.getChatId(),
//                    PHOTO_ERROR_MESSAGE
//            );
//            return;
//        }
//
//        if (dishDtos == null) {
//            calorieTelegramBot.sendReturnedMessage(message.getChatId(), "Не получилось обработать фото!");
//            return;
//        }
//        List<DishChoiceChatModel> dishChoiceChatModelList = dishChoiceChatModelService
//                .saveList(dishDtos, dishId);
//
//        calorieTelegramBot.sendReturnedMessage(
//                message.getChatId(),
//                getDishDtoListString(dishDtos),
//                getModelChooseListKeyboard(dishChoiceChatModelList),
//                null
//        );
//    }

    public InlineKeyboardMarkup getModelChooseListKeyboard(
            List<DishChoiceChatModel> dishChoiceList
    ) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        dishChoiceList.stream()
                .map(model -> TelegramKeyboard.button(
                        model.getChatModel().getName(),
                        getCallbackData(model)
                ))
                .forEach(builder::row);

        return builder.build();
    }

    private String getCallbackData(DishChoiceChatModel model) {
        StringBuilder sb = new StringBuilder();

        return sb
                .append(Command.CALORIE_CHOICE.getCommandText())
                .append(TelegramBot.DEFAULT_DELIMETER)
                .append(model.getId())
                .toString();
    }

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_GENERAL.getCommandText();
    }
}
