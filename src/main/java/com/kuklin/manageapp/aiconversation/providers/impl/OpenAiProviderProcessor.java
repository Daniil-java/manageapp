package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.OpenAiFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.aiconversation.providers.AiTextClient;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiInteractionRecordService;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiLogService;
import com.kuklin.manageapp.common.library.models.ByteArrayMultipartFile;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiProviderProcessor implements ProviderProcessor, AiTextClient {

    private final OpenAiFeignClient openAiFeignClient;
    private final MetricsAiLogService metricsAiLogService;
    private final MetricsAiInteractionRecordService metricsAiInteractionRecordService;

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

        OpenAiChatCompletionResponse response =
                openAiFeignClient.generate("Bearer " + aiKey, request);

        if (response != null && response.getUsage() != null) {
            metricsAiInteractionRecordService.saveInteractionRecord(
                    getProviderName(),
                    botIdentifier,
                    content,
                    MetricsAiInteractionRecord.AiMessageType.PHOTO,
                    response.getContent(),
                    MetricsAiInteractionRecord.AiMessageType.TEXT,
                    response.getUsage().getPromptTokens(),
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
        OpenAiChatCompletionResponse response =
                openAiFeignClient.generate("Bearer " + aiKey, request);

        if (response != null && response.getUsage() != null) {
            metricsAiInteractionRecordService.saveInteractionRecord(
                    getProviderName(),
                    botIdentifier,
                    request.getMessages().get(0).getContent().get(0).getText(),
                    messageType,
                    response.getContent(),
                    MetricsAiInteractionRecord.AiMessageType.TEXT,
                    response.getUsage().getPromptTokens(),
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

    public String fetchAudioResponse(
            String aiKey,
            byte[] content,
            BotIdentifier botIdentifier,
            String uniqLog
    ) {
        log.info(botIdentifier + "AUDIO! Uniq log: " + uniqLog);
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                "file-rus-or-eng-language",
                "audio.ogg",
                "audio/ogg",
                content
        );

        increaseMetricsLog();
        TranscriptionResponse response = openAiFeignClient.transcribeAudio(
                "Bearer " + aiKey,
                multipartFile,
                "whisper-1"
        );

        metricsAiInteractionRecordService.saveInteractionRecord(
                getProviderName(),
                botIdentifier,
                "[VOICE]",
                MetricsAiInteractionRecord.AiMessageType.VOICE,
                response.getText(),
                MetricsAiInteractionRecord.AiMessageType.TEXT,
                null,
                null
        );

        return response.getText();
    }

    private void increaseMetricsLog() {
        metricsAiLogService.incrementForProvider(getProviderName());
    }

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.OPENAI;
    }
}
