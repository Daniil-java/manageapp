package com.kuklin.manageapp.aiconversation.models.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kuklin.manageapp.aiconversation.models.BaseResponse;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiChatCompletionResponse extends BaseResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    private String systemFingerprint;

    @Override
    public String getContent() {
        return choices.get(0).getMessage().getContent();
    }

}
