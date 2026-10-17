package com.kuklin.manageapp.aiconversation.models.openai.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
public class OpenAiImageResponse {

    private List<ImageData> data;

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ImageData {
        private String url;
        @JsonProperty("b64_json")
        private String b64Json;
    }
}
