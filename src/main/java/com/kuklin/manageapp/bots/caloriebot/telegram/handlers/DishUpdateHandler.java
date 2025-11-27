package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.entities.DishChoiceChatModel;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.services.DishChoiceChatModelService;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgutils.Command;
import com.kuklin.manageapp.common.library.tgutils.TelegramKeyboard;
import com.kuklin.manageapp.common.services.TelegramService;
import com.kuklin.manageapp.common.services.TelegramUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;

@Component
@RequiredArgsConstructor
@Slf4j
public class DishUpdateHandler implements CalorieBotUpdateHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramService telegramService;
    private final TelegramCaloriesBotKeyComponents caloriesBotKeyComponents;
    private final DishService dishService;
    private final OpenAiProviderProcessor openAiIntegrationService;
    private final TelegramUserService telegramUserService;
    private final DishChoiceChatModelService dishChoiceChatModelService;
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String PHOTO_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать фото";
    private static final String ERROR_MESSAGE =
            "Данное сообщение не поддержтвается";
    private static final String ERROR_CONTENT_MESSAGE =
            "Это не съедобно!";
    private static final Long RESPONSE_LIMIT = 10L;
    private static final String ERROR_LIMIT_MSG = "Количество запросов, доступных вам, достигло предела!";


    @Override
    public void handle(Update update, TelegramUser telegramUser) {
        if (update.getMessage().hasPhoto() && !checkUserAccess(telegramUser)) {
            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), ERROR_LIMIT_MSG);
            return;
        }

        Dish dish = getDishOrNull(update, telegramUser);
        if (dish == null) return;

        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                Dish.getInfo(dish),
                getInlineMessage(dish.getId()),
                null
        );

    }

    private boolean checkUserAccess(TelegramUser telegramUser) {
        if (telegramUser.getTelegramId().equals(425120436L) ||
        telegramUser.getTelegramId().equals(420478432L)) return true;

        if (telegramUser.getResponseCount() > RESPONSE_LIMIT) return false;
        return true;
    }

    private Dish getDishOrNull(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getTelegramId();
        Dish dish;
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            dish = processPhotoOrNull(userId, update.getMessage());

            Map<ChatModel, DishDto> dishDtos = processPhotoOrNull(update.getMessage());
            if (dishDtos == null) {
                calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), "Не получилось обработать фото!");
                return null;
            }
            List<DishChoiceChatModel> dishChoiceChatModelList = dishChoiceChatModelService
                    .saveList(dishDtos, dish.getId());

            calorieTelegramBot.sendReturnedMessage(
                    update.getMessage().getChatId(),
                    getDishDtoListString(dishDtos),
                    getModelChooseListKeyboard(dishChoiceChatModelList),
                    null
            );

            telegramUserService.save(telegramUser.setResponseCount(telegramUser.getResponseCount() + 1));
        } else if (update.hasMessage() && update.getMessage().hasVoice()) {
            String request = processVoiceMessageOrNull(update.getMessage());
            dish = processTextOrNull(userId, request, update.getMessage().getChatId());
        } else {
            dish = processTextOrNull(userId, update.getMessage().getText(), update.getMessage().getChatId());
        }

        if (dish == null) {
            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), ERROR_CONTENT_MESSAGE);
            return null;
        }
        return dish;
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

    private Dish processTextOrNull(Long userId, String message, long chatId) {
        if (message == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, ERROR_MESSAGE);
            return null;
        }
        return dishService.getDishByDescriptionOrNull(userId, message);
    }

    private String processVoiceMessageOrNull(Message message) {
        Long chatId = message.getChatId();
        String request = convertVoiceToText(message);

        if (request == null) {
            calorieTelegramBot.sendReturnedMessage(chatId, VOICE_ERROR_MESSAGE);
            return null;
        }

        log.info(request);
        return request;
    }

    private String convertVoiceToText(Message message) {
        log.info("Скачивание аудиофайла с телеграмма...");
        String fileId = message.getVoice().getFileId();
        byte[] inputAudioFile = telegramService.downloadFileOrNull(calorieTelegramBot, fileId);
        if (inputAudioFile == null) {
            log.info("Аудиофайла не существует.");
            return null;
        }
        return openAiIntegrationService.fetchAudioResponse(caloriesBotKeyComponents.getAiKey(), inputAudioFile);
    }

    private Map<ChatModel, DishDto> processPhotoOrNull(Message message) {
        List<PhotoSize> photos = message.getPhoto();
        // Берём самое большое (последний элемент списка)
        PhotoSize photo = photos.get(photos.size() - 1);

        InputStream file = new ByteArrayInputStream(
                telegramService.downloadFileOrNull(calorieTelegramBot, photo.getFileId()));
        try {
            return dishService.getDishDtoByPhotoOrNullWithManyProviders(toBase64(file));
        } catch (IOException e) {
            log.error("Many providers connection request error!");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(), PHOTO_ERROR_MESSAGE);
            return null;
        } catch (Exception e) {
            log.error("Many providers request error!");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(), e.getLocalizedMessage());
            return null;
        }
    }

    private Dish processPhotoOrNull(Long userId, Message message) {
        List<PhotoSize> photos = message.getPhoto();
        // Берём самое большое (последний элемент списка)
        PhotoSize photo = photos.get(photos.size() - 1);

        InputStream file = new ByteArrayInputStream(
                telegramService.downloadFileOrNull(calorieTelegramBot, photo.getFileId()));
        try {
            return dishService.getDishDtoByPhotoOrNull(userId, toBase64(file));
        } catch (IOException e) {
            log.error("Provider error!");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(), "Один из провайдеров не смог обработать фото");
            return null;
        }
    }

    private static String toBase64(InputStream inputStream) throws IOException {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
    }

    public static InlineKeyboardMarkup getInlineMessage(Long dishId) {
        String callbackData = Command.CALORIE_DELETE.getCommandText() + TelegramBot.DEFAULT_DELIMETER + dishId;
        String buttonText = "Удалить из дневника";

        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setCallbackData(callbackData);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(Collections.singletonList(Collections.singletonList(button)));

        return markup;
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
