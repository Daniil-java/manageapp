package com.kuklin.manageapp.aiconversation.integrations;

import com.kuklin.manageapp.aiconversation.configurations.FeignClientConfig;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        value = "deepseek-feign-client",
        url = "https://api.deepseek.com/v1", // OpenAI-совместимый base_url
        configuration = FeignClientConfig.class
)
public interface DeepSeekFeignClient {
    @PostMapping("/chat/completions")
    OpenAiChatCompletionResponse generate(
            @RequestHeader("Authorization") String bearer,
            @RequestBody OpenAiChatCompletionRequest request
    );
}
