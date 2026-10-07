package com.kuklin.manageapp.aiconversation.models.yandex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuklin.manageapp.aiconversation.models.enums.ChatRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YandexAiRequest {

    @JsonProperty("modelUri")
    private String modelUri;

    @JsonProperty("completionOptions")
    private CompletionOptions completionOptions;

    @JsonProperty("messages")
    private List<YandexAiMessage> messages;
    private static final String DEF_MODEL_URL= "/yandexgpt-lite/latest";
    private static final String DEF_REQ_ROLE = ChatRole.USER.getValue();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class CompletionOptions {

        @JsonProperty("stream")
        private boolean stream;

        @JsonProperty("temperature")
        private double temperature;

        @JsonProperty("maxTokens")
        private int maxTokens;
    }

    public static YandexAiRequest makeDefaultRequest(String content, String folderId) {
        return new YandexAiRequest()
                .setModelUri("gpt://" + folderId + DEF_MODEL_URL)
                .setMessages(
                        List.of(
                                new YandexAiMessage()
                                        .setRole(DEF_REQ_ROLE)
                                        .setText(content)
                        )
                );
    }
}