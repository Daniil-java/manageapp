package com.kuklin.manageapp.aiconversation.models.claude;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ClaudeResponse{
    private List<ContentBlock> content; // ответ ассистента: обычно массив блоков

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
}
