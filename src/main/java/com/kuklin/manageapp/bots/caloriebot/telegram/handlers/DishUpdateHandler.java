package com.kuklin.manageapp.bots.caloriebot.telegram.handlers;

import com.kuklin.manageapp.bots.caloriebot.configurations.TelegramCaloriesBotKeyComponents;
import com.kuklin.manageapp.bots.caloriebot.entities.models.DishDto;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.bots.caloriebot.entities.Dish;
import com.kuklin.manageapp.bots.caloriebot.services.DishService;
import com.kuklin.manageapp.common.entities.TelegramUser;
import com.kuklin.manageapp.common.library.models.openai.ChatModel;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.services.OpenAiIntegrationService;
import com.kuklin.manageapp.common.services.TelegramService;
import com.kuklin.manageapp.common.library.tgutils.Command;
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
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DishUpdateHandler implements CalorieBotUpdateHandler{
    private final CalorieTelegramBot calorieTelegramBot;
    private final TelegramService telegramService;
    private final TelegramCaloriesBotKeyComponents caloriesBotKeyComponents;
    private final DishService dishService;
    private final OpenAiIntegrationService openAiIntegrationService;
    private static final String VOICE_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать голосовое сообщение";
    private static final String PHOTO_ERROR_MESSAGE =
            "Ошибка! Не получилось обработать фото";
    private static final String ERROR_MESSAGE =
            "Данное сообщение не поддержтвается";
    private static final String ERROR_CONTENT_MESSAGE =
            "Это не съедобно!";
    @Override
    public void handle(Update update, TelegramUser telegramUser) {

        Dish dish = getDishOrNull(update, telegramUser);
        if (dish == null) return;

        calorieTelegramBot.sendReturnedMessage(
                update.getMessage().getChatId(),
                Dish.getInfo(dish),
                getInlineMessage(dish.getId()),
                null
        );

    }

    private Dish getDishOrNull(Update update, TelegramUser telegramUser) {
        Long userId = telegramUser.getTelegramId();
        Dish dish;
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
//            dish = processPhotoOrNull(userId, update.getMessage());
            Map<ChatModel, DishDto> dishDtos = processPhotoOrNull(userId, update.getMessage());
            calorieTelegramBot.sendReturnedMessage(update.getMessage().getChatId(), getDishDtoListString(dishDtos));
            return null;
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
        String ls = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        map.forEach((model, dto) -> {
            if (dto != null) {
                sb.append(model != null ? model.getName() : "UNKNOWN_MODEL").append(ls)
                        .append(dto.toString()).append(ls);
            }
        });
        return sb.toString();
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

    private Map<ChatModel, DishDto> processPhotoOrNull(Long userId, Message message) {
        List<PhotoSize> photos = message.getPhoto();
        // Берём самое большое (последний элемент списка)
        PhotoSize photo = photos.get(photos.size() - 1);

        InputStream file = new ByteArrayInputStream(
                telegramService.downloadFileOrNull(calorieTelegramBot, photo.getFileId()));
        try {
            return dishService.getDishDtoByPhotoOrNullWithManyModels(toBase64(file));
        } catch (IOException e) {
            log.error("ERROR");
            calorieTelegramBot.sendReturnedMessage(message.getChatId(), PHOTO_ERROR_MESSAGE);
            return null;
        }
    }

//    private Dish processPhotoOrNull(Long userId, Message message) {
//        List<PhotoSize> photos = message.getPhoto();
//        // Берём самое большое (последний элемент списка)
//        PhotoSize photo = photos.get(photos.size() - 1);
//
//        InputStream file = new ByteArrayInputStream(
//                telegramService.downloadFileOrNull(calorieTelegramBot, photo.getFileId()));
//        try {
//            return dishService.getDishDtoByPhotoOrNull(userId, toBase64(file));
//        } catch (IOException e) {
//            log.error("ERROR");
//            calorieTelegramBot.sendReturnedMessage(message.getChatId(), PHOTO_ERROR_MESSAGE);
//            return null;
//        }
//    }

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

    @Override
    public String getHandlerListName() {
        return Command.CALORIE_GENERAL.getCommandText();
    }
}
