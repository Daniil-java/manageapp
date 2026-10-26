package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.OpenAiFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiProcessorException;
import com.kuklin.manageapp.aiconversation.models.AiQuotaExhaustedEvent;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import feign.FeignException;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.aiconversation.models.openai.image.OpenAiImageRequest;
import com.kuklin.manageapp.aiconversation.models.openai.image.OpenAiImageResponse;
import com.kuklin.manageapp.aiconversation.providers.AiTextClient;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiInteractionRecordService;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiLogService;
import com.kuklin.manageapp.common.library.models.ByteArrayMultipartFile;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import com.kuklin.manageapp.common.library.utils.FilesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiProviderProcessor implements ProviderProcessor, AiTextClient {

    // Расшифровка голоса для всех ботов. whisper-1 путал язык на коротких фразах
    // («кофе» → португальский, японский), mini-transcribe точнее и дешевле
    private static final String TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";

    private final OpenAiFeignClient openAiFeignClient;
    private final MetricsAiLogService metricsAiLogService;
    private final MetricsAiInteractionRecordService metricsAiInteractionRecordService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AiResponse fetchResponsePhotoOrNull(
            String imagerUrl, String
            content, ChatModel chatModel,
            String aiKey,
            BotIdentifier botIdentifier,
            String uniqLog
    ) {
        log.info(botIdentifier + "PHOTO! Uniq log: " + uniqLog);
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultImgRequest(content, imagerUrl, chatModel);
        increaseMetricsLog();

        OpenAiChatCompletionResponse response = generateOrThrow(aiKey, request);

        if (response.getUsage() != null) {
            metricsAiInteractionRecordService.saveInteractionRecord(
                    getProviderName(),
                    botIdentifier,
                    request.getModel(),
                    content,
                    MetricsAiInteractionRecord.AiMessageType.PHOTO,
                    response.getContent(),
                    MetricsAiInteractionRecord.AiMessageType.TEXT,
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCachedTokensOrZero(),
                    response.getUsage().getCompletionTokens()
            );
        }

        return response.toAiResponse();
    }

    public String fetchPhotoResponse(
            String aiKey, String content, String imageUrl, BotIdentifier botIdentifier
    ) {
        log.info("OpenAI: Photo request!");
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultImgRequest(content, imageUrl);

        return fetchResponse(aiKey, request, botIdentifier, MetricsAiInteractionRecord.AiMessageType.PHOTO);
    }

    private String fetchResponse(
            String aiKey,
            OpenAiChatCompletionRequest request,
            BotIdentifier botIdentifier,
            MetricsAiInteractionRecord.AiMessageType messageType
    ) {
        increaseMetricsLog();
        OpenAiChatCompletionResponse response = generateOrThrow(aiKey, request);

        if (response.getUsage() != null) {
            metricsAiInteractionRecordService.saveInteractionRecord(
                    getProviderName(),
                    botIdentifier,
                    request.getModel(),
                    request.getMessages().get(0).getContent().get(0).getText(),
                    messageType,
                    response.getContent(),
                    MetricsAiInteractionRecord.AiMessageType.TEXT,
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCachedTokensOrZero(),
                    response.getUsage().getCompletionTokens()
            );
        }

        return response.getChoices().get(0).getMessage().getContent();
    }

    @Override
    public String fetchResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            MetricsAiInteractionRecord.AiMessageType messageType) {
        log.info(botIdentifier + " uniq log: " + uniqLog);
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultRequest(content);
        return fetchResponse(aiKey, request, botIdentifier, messageType);

    }

    /**
     * Текстовый запрос с высокой температурой: ответы на похожие данные не повторяются.
     */
    public String fetchCreativeResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            MetricsAiInteractionRecord.AiMessageType messageType) {
        log.info(botIdentifier + " uniq log: " + uniqLog);
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeCreativeRequest(content);
        return fetchResponse(aiKey, request, botIdentifier, messageType);
    }

    /**
     * JSON-режим с высокой температурой: ответ — один JSON-объект, тексты не повторяются.
     */
    public String fetchCreativeJsonResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            MetricsAiInteractionRecord.AiMessageType messageType) {
        log.info(botIdentifier + " uniq log: " + uniqLog);
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeCreativeJsonRequest(content);
        return fetchResponse(aiKey, request, botIdentifier, messageType);
    }

    /**
     * Текстовый запрос в JSON-режиме: ответ — всегда один JSON-объект.
     */
    public String fetchJsonResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            MetricsAiInteractionRecord.AiMessageType messageType) {
        log.info(botIdentifier + " uniq log: " + uniqLog);
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeJsonRequest(content);
        return fetchResponse(aiKey, request, botIdentifier, messageType);
    }

    public String fetchAudioResponse(
            String aiKey,
            byte[] content,
            BotIdentifier botIdentifier,
            String uniqLog
    ) {
        return fetchAudioResponse(aiKey, content, "audio.ogg", "audio/ogg", botIdentifier, uniqLog);
    }

    // fileName важен: модель определяет формат аудио по расширению (audio.webm, audio.mp4, ...)
    public String fetchAudioResponse(
            String aiKey,
            byte[] content,
            String fileName,
            String contentType,
            BotIdentifier botIdentifier,
            String uniqLog
    ) {
        return fetchAudioResponse(aiKey, content, fileName, contentType, null, botIdentifier, uniqLog);
    }

    /**
     * Расшифровка голоса с подсказкой.
     * prompt — тема и примеры слов (например, еда для калорийного бота): короткие фразы
     * без подсказки модель часто расшифровывает не на том языке. null — без подсказки.
     */
    public String fetchAudioResponse(
            String aiKey,
            byte[] content,
            String fileName,
            String contentType,
            String prompt,
            BotIdentifier botIdentifier,
            String uniqLog
    ) {
        log.info(botIdentifier + "AUDIO! Uniq log: " + uniqLog);
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                "file-rus-or-eng-language",
                fileName,
                contentType,
                content
        );

        increaseMetricsLog();
        TranscriptionResponse response;
        try {
            response = prompt == null || prompt.isBlank()
                    ? openAiFeignClient.transcribeAudio("Bearer " + aiKey, multipartFile, TRANSCRIPTION_MODEL)
                    : openAiFeignClient.transcribeAudioWithPrompt("Bearer " + aiKey, multipartFile, TRANSCRIPTION_MODEL, prompt);
        } catch (FeignException e) {
            throw buildUnavailable("transcription", e);
        }
        if (response == null || response.getText() == null) {
            throw new AiUnavailableException("OpenAI transcription: empty response", 0, null);
        }

        TranscriptionResponse.TranscriptionUsage usage = response.getUsage();
        metricsAiInteractionRecordService.saveInteractionRecord(
                getProviderName(),
                botIdentifier,
                TRANSCRIPTION_MODEL,
                "[VOICE]",
                MetricsAiInteractionRecord.AiMessageType.VOICE,
                response.getText(),
                MetricsAiInteractionRecord.AiMessageType.TEXT,
                usage != null ? usage.getInputTokens() : null,
                0L,
                usage != null ? usage.getOutputTokens() : null
        );

        return response.getText();
    }

    public byte[] generateImageBytes(
            String aiKey,
            String prompt,
            BotIdentifier botIdentifier,
            String uniqLog
    ) throws AiProcessorException {
        log.info(botIdentifier + " IMAGE! Uniq log: " + uniqLog);

        increaseMetricsLog();

        OpenAiImageRequest request = OpenAiImageRequest.builder()
                .prompt(prompt)
                .model(OpenAiImageRequest.MODEL_GPT_IMAGE_2_5_FLARE)
                .size(OpenAiImageRequest.SIZE_1024)
                .build();

        OpenAiImageResponse response =
                openAiFeignClient.generateImage("Bearer " + aiKey, request);

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            throw new AiProcessorException("Failed to generate image: empty response");
        }

        OpenAiImageResponse.ImageData image = response.getData().get(0);

        try {
            // 1. Если пришел base64 — декодим
            if (image.getB64Json() != null) {
                return Base64.getDecoder().decode(image.getB64Json());
            }

            // ✅ 2. Если пришел URL — скачиваем
            if (image.getUrl() != null) {
                return FilesUtils.downloadImage(image.getUrl());
            }

        } catch (Exception e) {
            throw new AiProcessorException("Failed to process image response", e);
        }

        throw new AiProcessorException("Image response has no usable data");
    }

    /**
     * Запрос к chat/completions. Ошибка провайдера (401, 429, 5xx), таймаут, обрыв сети или ответ без текста —
     * AiUnavailableException: вызывающий код отличает «ИИ не ответил» от «ИИ ответил, но не то».
     */
    private OpenAiChatCompletionResponse generateOrThrow(String aiKey, OpenAiChatCompletionRequest request) {
        OpenAiChatCompletionResponse response;
        try {
            response = openAiFeignClient.generate("Bearer " + aiKey, request);
        } catch (FeignException e) {
            throw buildUnavailable("chat " + request.getModel(), e);
        }
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new AiUnavailableException("OpenAI chat " + request.getModel() + ": empty response", 0, null);
        }
        return response;
    }

    // RetryableException (таймаут, сеть) — тоже FeignException, у неё status() = -1
    private AiUnavailableException buildUnavailable(String what, FeignException e) {
        int status = Math.max(e.status(), 0);
        if (isQuotaExhausted(e)) {
            // Не log.error: общий алерт об ошибке заменяет отдельный, со статистикой расходов
            log.warn("OpenAI {} failed: quota exhausted, HTTP {} {}", what, status, e.getMessage());
            eventPublisher.publishEvent(new AiQuotaExhaustedEvent(getProviderName(), "HTTP " + status + " " + e.getMessage()));
        } else {
            log.error("OpenAI {} failed: HTTP {} {}", what, status, e.getMessage());
        }
        return new AiUnavailableException("OpenAI " + what + " failed: HTTP " + status, status, e);
    }

    /**
     * Кончились деньги на счёте: 429 с кодом insufficient_quota (обычный 429 — превышена частота, пройдёт сам)
     * или достигнут жёсткий лимит расходов из панели OpenAI.
     */
    static boolean isQuotaExhausted(FeignException e) {
        String body = e.contentUTF8();
        if (body == null) return false;
        return (e.status() == 429 && body.contains("insufficient_quota"))
                || body.contains("billing_hard_limit_reached");
    }

    private void increaseMetricsLog() {
        metricsAiLogService.incrementForProvider(getProviderName());
    }

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.OPENAI;
    }
}
