package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.DeepSeekFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.aiconversation.providers.ProviderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeepSeekProviderProcessor implements ProviderProcessor {

    private final DeepSeekFeignClient deepSeekFeignClient;

    @Override
    public ProviderVariant getProviderName() {
        return ProviderVariant.DEEPSEEK;
    }

    @Override
    public AiResponse fetchResponsePhotoOrNull(String imgUrl, String content, ChatModel chatModel, String aiKey) {
        try {
            OpenAiChatCompletionRequest req =
                    OpenAiChatCompletionRequest.makeDefaultImgRequest(content, imgUrl)
                            .setModel(chatModel.getName());

            OpenAiChatCompletionResponse resp =
                    deepSeekFeignClient.generate("Bearer " + aiKey, req);

            return resp.toAiResponse();

        } catch (Exception e) {
            log.error("DeepSeek photo call failed", e);
            return null;
        }
    }
}
