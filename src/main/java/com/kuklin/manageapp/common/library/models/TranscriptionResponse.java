package com.kuklin.manageapp.common.library.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
public class TranscriptionResponse {
    private String text;
    // gpt-4o-*-transcribe отдают токены; whisper-1 — длительность (type = duration), тогда токенов нет
    private TranscriptionUsage usage;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class TranscriptionUsage {
        private String type;
        private Long inputTokens;
        private Long outputTokens;
    }
}
