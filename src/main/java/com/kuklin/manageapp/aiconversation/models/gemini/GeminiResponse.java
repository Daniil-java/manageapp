package com.kuklin.manageapp.aiconversation.models.gemini;

import com.kuklin.manageapp.aiconversation.models.BaseResponse;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class GeminiResponse extends BaseResponse {
    private List<Candidate> candidates;
    private UsageMetadata usageMetadata;
    private String modelVersion;

    @Data
    @Accessors(chain = true)
    public static class Candidate {
        private Content content;
    }

    @Data
    @Accessors(chain = true)
    public static class UsageMetadata {
        private Integer promptTokenCount;
        private Integer candidatesTokenCount;
        private Integer totalTokenCount;
    }

    @Override
    protected String getContent() {
        return candidates.get(0).getContent().toString();
    }

    public String firstTextOrEmpty() {
        if (candidates == null) return "";

        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.getContent() == null) continue;
            List<Content.Part> parts = candidate.getContent().getParts();
            if (parts == null) continue;

            for (Content.Part part : parts) {
                if (part != null && part.getText() != null && !part.getText().isBlank()) {
                    return part.getText();
                }
            }
        }
        return "";
    }
}
