package com.kuklin.manageapp.common.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.providers.impl.OpenAiProviderProcessor;
import com.kuklin.manageapp.bots.caloriebot.telegram.CalorieTelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBot;
import com.kuklin.manageapp.common.library.tgmodels.TelegramBotClient;
import com.kuklin.manageapp.common.library.tgutils.TelegramBotFile;
import com.kuklin.manageapp.common.configurations.feignclients.TelegramFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final TelegramFeignClient telegramFeignClient;
    private final OpenAiProviderProcessor openAiProviderProcessor;

    public TelegramBotFile getFileOrNull(TelegramBotClient telegramBot, String fileId) {
        try {
            return new ObjectMapper().readValue(
                    telegramFeignClient.getFileRaw(
                            telegramBot.getToken(), fileId),
                    TelegramBotFile.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public byte[] downloadFile(TelegramBotClient telegramBot, TelegramBotFile telegramBotFile) {
        return telegramFeignClient.downloadFile(
                telegramBot.getToken(),
                telegramBotFile.getResult().getFilePath());
    }

    public byte[] downloadFileOrNull(TelegramBotClient telegramBot, String fileId) {
        TelegramBotFile telegramBotFile = getFileOrNull(telegramBot, fileId);
        if (telegramBotFile == null) return null;

        return downloadFile(telegramBot, telegramBotFile);
    }

    public String convertVoiceToTextOrNull(TelegramBotClient telegramBot, String aiKey, Message message) {
        log.info("Скачивание аудиофайла с телеграмма...");
        String fileId = message.getVoice().getFileId();
        byte[] inputAudioFile = downloadFileOrNull(telegramBot, fileId);
        if (inputAudioFile == null) {
            log.info("Аудиофайла не существует.");
            return null;
        }
        return openAiProviderProcessor.fetchAudioResponse(
                aiKey,
                inputAudioFile,
                CalorieTelegramBot.BOT_IDENTIFIER,
                this.getClass().getSimpleName()
        );
    }

    public String downloadPhotoFileBase64OrNull(TelegramBot telegramBot, Message message) throws IOException {
        if (!message.hasPhoto()) return null;
        InputStream file = downloadPhotoFile(telegramBot, message);
        return toBase64(file);
    }

    private InputStream downloadPhotoFile(TelegramBot telegramBot, Message message) {
        List<PhotoSize> photos = message.getPhoto();
        // Берём самое большое (последний элемент списка)
        PhotoSize photo = photos.get(photos.size() - 1);

        return new ByteArrayInputStream(
                downloadFileOrNull(telegramBot, photo.getFileId()));
    }

    private static String toBase64(InputStream inputStream) throws IOException {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
    }

}
