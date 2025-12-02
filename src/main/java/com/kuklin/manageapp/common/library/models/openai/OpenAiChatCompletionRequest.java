package com.kuklin.manageapp.common.library.models.openai;

import com.kuklin.manageapp.common.library.models.PhotoMessageWithPrompt;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OpenAiChatCompletionRequest {
    private String model;
    private List<PhotoMessageWithPrompt> messages;
    private float temperature;

    public static final float TEMPERATURE_DEFAULT = 0.1f;
    private static final String MODEL_DEFAULT = "gpt-4o";

    public static OpenAiChatCompletionRequest makeDefaultRequest(
            String content) {

        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(MODEL_DEFAULT)
                .setMessages(PhotoMessageWithPrompt.getFirstMessage(content));
    }

    public static OpenAiChatCompletionRequest makeDefaultImgRequest(String content, String imageUrl) {
        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(MODEL_DEFAULT)
                .setMessages(PhotoMessageWithPrompt.getMessageList(content, imageUrl));
    }

}
