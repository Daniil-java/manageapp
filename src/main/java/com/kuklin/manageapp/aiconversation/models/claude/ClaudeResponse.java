package com.kuklin.manageapp.aiconversation.models.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ClaudeResponse{
    private List<ContentBlock> content; // ответ ассистента: обычно массив блоков
    private Usage usage;

    @Data @Accessors(chain = true)
    public static class ContentBlock {
        private String type; // "text"
        private String text;
    }

    public String firstTextOrEmpty() {
        if (content == null) return "";
        for (ContentBlock b : content) {
            if (b != null && "text".equals(b.getType()) &&
                    b.getText() != null && !b.getText().isBlank()) {
                return b.getText();
            }
        }
        return "";
    }

    @Data
    @Accessors(chain = true)
    public static class Usage {

        @JsonProperty("input_tokens")
        private Long inputTokens;

        @JsonProperty("output_tokens")
        private Long outputTokens;
    }
}
