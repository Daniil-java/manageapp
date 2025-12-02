package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.OpenAiFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import com.kuklin.manageapp.common.library.models.ByteArrayMultipartFile;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiProviderProcessor implements ProviderProcessor {

    private final OpenAiFeignClient openAiFeignClient;

    @Override
    public AiResponse fetchResponsePhotoOrNull(String imagerUrl, String content, ChatModel chatModel, String aiKey) {
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultImgRequest(content, imagerUrl, chatModel);

        OpenAiChatCompletionResponse response =
                openAiFeignClient.generate("Bearer " + aiKey, request);

        return response.toAiResponse();
    }

    public String fetchResponse(String aiKey, String content) {
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultRequest(content);

        return fetchResponse(aiKey, request);
    }

    public String fetchPhotoResponse(String aiKey, String content, String imageUrl) {
        OpenAiChatCompletionRequest request =
                OpenAiChatCompletionRequest.makeDefaultImgRequest(content, imageUrl);

        return fetchResponse(aiKey, request);
    }

    public List<String> fetchResponseFromManyModels(String aiKey, String content) {
        List<String> responses = new ArrayList<>();
        for (ChatModel chatModel: ChatModel.getModels()) {
            OpenAiChatCompletionRequest request =
                    OpenAiChatCompletionRequest.makeModelRequest(content, chatModel);
            try {
                responses.add(fetchResponse(aiKey, request));
            } catch (Exception e) {
                log.error("OpenAI Connection Error!");
            }
        }
        return responses;
    }

    public Map<ChatModel, String> fetchResponseFromManyModels(String aiKey, String content, String imageUrl) {
        Map<ChatModel, String> responses = new HashMap<>();
        for (ChatModel chatModel: ChatModel.getModels()) {
            OpenAiChatCompletionRequest request =
                    OpenAiChatCompletionRequest.makeModelImgRequest(content, chatModel, imageUrl);
            try {
                responses.put(chatModel, fetchResponse(aiKey, request));
            } catch (Exception e) {
                log.error("OpenAI Connection Error!");
            }
        }
        return responses;
    }

    private String fetchResponse(String aiKey, OpenAiChatCompletionRequest request) {
        OpenAiChatCompletionResponse response =
                openAiFeignClient.generate("Bearer " + aiKey, request);

        return response.getChoices().get(0).getMessage().getContent();
    }

    public String fetchAudioResponse(String aiKey, byte[] content) {
        MultipartFile multipartFile = new ByteArrayMultipartFile(
                "file",
                "audio.ogg",
                "audio/ogg",
                content
        );

        TranscriptionResponse response = openAiFeignClient.transcribeAudio(
                "Bearer " + aiKey,
                multipartFile,
                "whisper-1"
        );

        return response.getText();
    }

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.OPENAI;
    }
}
