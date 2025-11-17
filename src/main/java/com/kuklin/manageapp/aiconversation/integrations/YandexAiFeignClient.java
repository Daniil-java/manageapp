package com.kuklin.manageapp.aiconversation.integrations;

import com.kuklin.manageapp.aiconversation.configurations.FeignClientConfig;
import com.kuklin.manageapp.aiconversation.models.yandex.YandexAiRequest;
import com.kuklin.manageapp.aiconversation.models.yandex.YandexAiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        value = "yandex-ai-feign-client",
        url = "https://llm.api.cloud.yandex.net",
        configuration = FeignClientConfig.class
)
public interface YandexAiFeignClient {

    @PostMapping(
            value = "/foundationModels/v1/completion",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    YandexAiResponse generate(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "x-folder-id", required = false) String folderId,
            @RequestBody YandexAiRequest request
    );
}
