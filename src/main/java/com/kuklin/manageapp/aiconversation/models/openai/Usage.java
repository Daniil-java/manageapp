package com.kuklin.manageapp.aiconversation.models.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.experimental.Accessors;

// OpenAI и DeepSeek отдают prompt_tokens / completion_tokens / total_tokens
@Data
@Accessors(chain = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Usage {
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    // Сколько входных токенов OpenAI взял из кэша (повторяющееся начало промпта). У DeepSeek нет — null
    private PromptTokensDetails promptTokensDetails;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PromptTokensDetails {
        private Long cachedTokens;
    }

    public long getCachedTokensOrZero() {
        return promptTokensDetails != null && promptTokensDetails.getCachedTokens() != null
                ? promptTokensDetails.getCachedTokens() : 0L;
    }
}
