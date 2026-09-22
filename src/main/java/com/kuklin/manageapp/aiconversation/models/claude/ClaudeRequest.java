package com.kuklin.manageapp.aiconversation.models.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ClaudeRequest {
    private String model;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    private List<Message> messages;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String role; // "user" | "assistant"
        private List<Block> content; // смесь text+image
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Block {
        private String type;  // "text" | "image"
        private String text;  // если type="text"
        private Source source; // если type="image"

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Source {
            private String type;       // "base64" | "url"
            @JsonProperty("media_type")
            private String mediaType; // "image/jpeg" | "image/png" | ...
            private String data;       // base64 (если type="base64")
            private String url;        // public URL (если type="url")
        }
    }

    // Фабрика: text + inline image (base64)
    public static ClaudeRequest ofBase64Image(
            String model, int maxTokens, String text, String mime, String b64
    ) {
        Block image = new Block()
                .setType("image")
                .setSource(new Block.Source()
                        .setType("base64")
                        .setMediaType(mime)
                        .setData(b64));     // url НЕ задаём

        Block prompt = new Block()
                .setType("text")
                .setText(text);

        Message m = new Message()
                .setRole("user")
                .setContent(List.of(image, prompt));

        return new ClaudeRequest()
                .setModel(model)
                .setMaxTokens(maxTokens)
                .setMessages(List.of(m));
    }

    // Вариант: картинка по URL + текст
    public static ClaudeRequest ofUrlImage(
            String model, int maxTokens, String text, String url
    ) {
        Block image = new Block()
                .setType("image")
                .setSource(new Block.Source()
                        .setType("url")
                        .setUrl(url));      // media_type/data НЕ задаём

        Block prompt = new Block()
                .setType("text")
                .setText(text);

        Message m = new Message()
                .setRole("user")
                .setContent(List.of(image, prompt));

        return new ClaudeRequest()
                .setModel(model)
                .setMaxTokens(maxTokens)
                .setMessages(List.of(m));
    }
}
