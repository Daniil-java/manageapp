package com.kuklin.manageapp.bots.caloriebot.telegram.handlers.dish;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.AccessResult;
import com.kuklin.manageapp.bots.caloriebot.featurerestrictions.MissingFeatureException;
import com.kuklin.manageapp.bots.caloriebot.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.services.AnalyticsService;
import com.kuklin.manageapp.bots.caloriebot.services.CalorieAccessService;
import com.kuklin.manageapp.bots.caloriebot.services.DishChoiceChatModelService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.telegram.handlers.CalorieBotUpdateHandler;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramService;
import com.kuklin.manageapp.payment.handlers.PaymentPlanListUpdateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;

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
    private static final String PORTION_COUNT_CMD = "PC";
    private static final String PORTION_WEIGHT_CMD = "PW";
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String PHOTO_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать фото";
    private static final String ERROR_MESSAGE =
            "Данное сообщение не поддержтвается";
    private static final String ERROR_CONTENT_MESSAGE =
            "Это не съедобно!";
    private static final String SUB_MESSAGE =
            "Для использования бота необходимо приобрести подписку! Введите команду /plan";
    private static final String ERROR_LIMIT_MSG = "Количество запросов, доступных вам, достигло предела!";
    private static final String ACCESS_DENIED_MSG = "Доступ ограничен!";

    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        Long chatId = update.hasPreCheckoutQuery()
                ? update.getCallbackQuery().getMessage().getChatId()
                : update.getMessage().getChatId();

        List<Dish> dishes = getDishOrNull(update, telegramUser);
        if (dishes == null || dishes.isEmpty()) return;

        for (Dish dish: dishes) {
            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    analyticsService.getInfo(dish, telegramUser.getTelegramId()),
                    getPortionWeightKeyboard(dish),
                    null
            );
        }
    }

    /**
     * Обрабатывает апдейт и пытается получить Dish.
     * Возвращает:
     * - Dish, если всё прошло успешно;
     * - null, если были ошибки (при этом в большинстве случаев уже отправлены сообщения пользователю).
     */
    private List<Dish> getDishOrNull(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getTelegramId();
        List<Dish> dishes;

        // ==== ВЕТКА 1: пользователь прислал фото ====
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            try {
                dishes = processPhotoOrNull(telegramUser, update.getMessage());
            } catch (MissingFeatureException e) {
                calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), ACCESS_DENIED_MSG);
                return null;
            }
            // ==== ВЕТКА 2: пользователь прислал голосовое ====
        } else if (update.hasMessage() && update.getMessage().hasVoice()) {
            String request = processVoiceMessageOrNull(update.getMessage());
            // Если не смогли распознать голос — дальше не идём
            if (request == null) {
                return null;
            }
            dishes = processTextOrNull(userId, request, update.getMessage().getChatId());
            // ==== ВЕТКА 3: всё остальное считаем текстовым сообщением ====
        } else {
            dishes = processTextOrNull(userId, update.getMessage().getText(), update.getMessage().getChatId());
        }

        if (dishes == null || dishes.isEmpty()) {
            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), ERROR_CONTENT_MESSAGE);
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
            calorieTelegramBot.sendReturnedMessage(chatId, ERROR_MESSAGE);
            return null;
        }
        return dishService.getDishByDescriptionOrNull(userId, message);
    }

    private String processVoiceMessageOrNull(Message message) {
        Long chatId = message.getChatId();
        String request = telegramService.convertVoiceToTextOrNull(
                calorieTelegramBot,
                caloriesBotKeyComponents.getAiKey(),
                message);

        if (request == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, VOICE_ERROR_MESSAGE);
            return null;
        }

        log.info(request);
        return request;
    }

    private List<Dish> processPhotoOrNull(TelegramUser telegramUser, Message message) throws MissingFeatureException {
        try {
            String photoBase64 = telegramService.downloadPhotoFileBase64OrNull(calorieTelegramBot, message);
            AccessResult<List<Dish>> dishResult = dishService.getDishDtoByPhoto(
                    telegramUser.getTelegramId(), photoBase64, message.getCaption());
            List<Dish> dishes = dishResult.getOrThrow();
            calorieAccessService.incrementResponses(telegramUser);
            if (dishes == null || dishes.isEmpty()) return null;
//        processManyAiModels(dish.getId(), photoBase64, message);

            return dishes;
        } catch (IOException e) {
            log.error("Provider error!");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(), "Один из провайдеров не смог обработать фото");
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

    public static InlineKeyboardMarkup getPortionKeyboardFavorite(Dish dish) {
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                + TelegramBot.DEFAULT_DELIMETER;

        // здесь просто копируем ряды, но кнопку "Сохранить блюдо"
        // меняем на "✅ В избранном" и делаем её неактивной

        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("Вес порции:", "temp"),
                        TelegramKeyboard.button("-10%", base + "-10"),
                        TelegramKeyboard.button("+10%", base + "10")
                );
        if (dish.getPortions() > 1) {
            builder.row(
                    TelegramKeyboard.button("Порций:", "temp"),
                    TelegramKeyboard.button(
                            "➖",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() - 1)
                    ),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() + 1)
                    )
            );
        } else {
            // Если порция одна — показываем только "+"
            builder.row(
                    TelegramKeyboard.button("Порций:", "temp"),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + 2
                    )
            );
        }
        return builder
                .row(
                        TelegramKeyboard.button(
                                "✅ В избранном",
                                // сюда поставь callback для игнорируемой кнопки
                                // если у тебя в TelegramKeyboard есть что-то типа IGNORE/EMPTY – используй его
                                "temp" //Заглушка
                        )
                )
                .row(
                        TelegramKeyboard.button(
                                "Удалить из дневника",
                                Command.CALORIE_DELETE.getCommandText()
                                        + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                        )
                )
                .build();


    }

    public static InlineKeyboardMarkup getPortionWeightKeyboard(Dish dish) {
        // Базовая часть для всех кнопок изменения порции
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                + TelegramBot.DEFAULT_DELIMETER;

        //<scale_command>delim<dishId>delim<+-scale>
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("Вес порции:", "temp"),
                        TelegramKeyboard.button("-10%", base + "-10"),
                        TelegramKeyboard.button("+10%", base + "10")
                );
        if (dish.getPortions() > 1) {
            builder.row(
                    TelegramKeyboard.button("Порций:", "temp"),
                    TelegramKeyboard.button(
                            "➖",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() - 1)
                    ),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + (dish.getPortions() + 1)
                    )
            );
        } else {
            // Если порция одна — показываем только "+"
            builder.row(
                    TelegramKeyboard.button("Порций:", "temp"),
                    TelegramKeyboard.button(
                            "➕",
                            base + PORTION_COUNT_CMD
                                    + TelegramBot.DEFAULT_DELIMETER + 2
                    )
            );
        }
        return builder
                .row(
                        TelegramKeyboard.button(
                                "⭐ Сохранить блюдо",
                                Command.CALORIE_FAVORITE_ADD.getCommandText()
                                        + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                        )
                )
                .row(
                        TelegramKeyboard.button(
                                "Удалить из дневника",
                                Command.CALORIE_DELETE.getCommandText()
                                        + TelegramBot.DEFAULT_DELIMETER + dish.getId()
                        )
                )
                .build();
    }

    public static InlineKeyboardMarkup getPortionKeyboard(Long dishId) {
        // Базовая часть для всех кнопок изменения порции
        String base = Command.CALORIE_SCALE.getCommandText()
                + TelegramBot.DEFAULT_DELIMETER + dishId
                + TelegramBot.DEFAULT_DELIMETER;

        //<scale_command>delim<dishId>delim<+-scale>
        return TelegramKeyboard.builder()
                .row(
                        TelegramKeyboard.button("-50%", base + "-50"),
                        TelegramKeyboard.button("-10%", base + "-10"),
//                        TelegramKeyboard.button("ОК",   base + "OK"),
                        TelegramKeyboard.button("+10%", base + "10"),
                        TelegramKeyboard.button("+50%", base + "50")
                )
                .row(
                        TelegramKeyboard.button(
                                "⭐ Сохранить блюдо",
                                Command.CALORIE_FAVORITE_ADD.getCommandText()
                                        + TelegramBot.DEFAULT_DELIMETER + dishId
                        )
                )
                .row(
                        TelegramKeyboard.button(
                                "Удалить из дневника",
                                Command.CALORIE_DELETE.getCommandText()
                                        + TelegramBot.DEFAULT_DELIMETER + dishId
                        )
                )
                .build();
    }

    public InlineKeyboardMarkup getModelChooseListKeyboard(
            List<DishChoiceChatModel> dishChoiceList
    ) {
        TelegramKeyboard.TelegramKeyboardBuilder builder = TelegramKeyboard.builder();

        dishChoiceList.stream()
                .map(model -> TelegramKeyboard.button(
                        model.getChatModel().getName(),
                        getCallbackData(model)
                ))
                .forEach(button -> builder.row(button));

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
