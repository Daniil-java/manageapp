package com.kuklin.manageapp.aiconversation.models.openai;

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
}
