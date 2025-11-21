package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.YandexAiFeignClient;
import com.kuklin.manageapp.aiconversation.models.yandex.YandexAiRequest;
import com.kuklin.manageapp.aiconversation.models.yandex.YandexAiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class YandexAiProviderProcessor {
    private final YandexAiFeignClient yandexClient;

    public String fetchResponse(String aiKey, String folderId, String content) {
        YandexAiRequest yandexAiRequest = YandexAiRequest.makeDefaultRequest(content, folderId);

        YandexAiResponse response = yandexClient.generate(
                "Api-Key " + aiKey,
                folderId,
                yandexAiRequest
        );

        return response.getContent();
    }
}
