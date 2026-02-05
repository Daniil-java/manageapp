package com.kuklin.manageapp.aiconversation.models.yandex;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class YandexAiResponse {

    @JsonProperty("result")
    private Result result;

    public String getContent() {
        return this.result.getAlternatives().get(0).getMessage().getText();
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Result {

        @JsonProperty("alternatives")
        private List<Alternative> alternatives;

        @JsonProperty("usage")
        private Usage usage;

        @JsonProperty("modelVersion")
        private String modelVersion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Alternative {

        @JsonProperty("message")
        private YandexAiMessage message;

        @JsonProperty("status")
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class Usage {

        // В ответе это строки, поэтому String
        @JsonProperty("inputTextTokens")
        private String inputTextTokens;

        @JsonProperty("completionTokens")
        private String completionTokens;

        @JsonProperty("totalTokens")
        private String totalTokens;

        @JsonProperty("completionTokensDetails")
        private CompletionTokensDetails completionTokensDetails;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    public static class CompletionTokensDetails {

        @JsonProperty("reasoningTokens")
        private String reasoningTokens;
    }
}
