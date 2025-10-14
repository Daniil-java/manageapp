package com.kuklin.manageapp.common.services;

import com.kuklin.manageapp.common.configurations.feignclients.OpenAiFeignClient;
import com.kuklin.manageapp.common.library.models.ByteArrayMultipartFile;
import com.kuklin.manageapp.common.library.models.openai.ChatModel;
import com.kuklin.manageapp.common.library.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.common.library.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiIntegrationService {

    private final OpenAiFeignClient openAiFeignClient;
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

}
