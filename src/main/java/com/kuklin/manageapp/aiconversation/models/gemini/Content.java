package com.kuklin.manageapp.aiconversation.models.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Content {
    private String role;
    private List<Part> parts;

    @Data
    @Accessors(chain = true)
    public static class Part {
        private String text;
        @JsonProperty("inline_data")
        private InlineData inlineData;

        @Data
        @Accessors(chain = true)
        public static class InlineData {
            @JsonProperty("mime_type")
            private String mimeType;
            private String data;
        }
    }
}
