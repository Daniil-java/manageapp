package com.kuklin.manageapp.aiconversation.models.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kuklin.manageapp.aiconversation.models.PhotoMessageWithPrompt;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OpenAiChatCompletionRequest {
    private String model;
    private List<PhotoMessageWithPrompt> messages;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Float temperature;

    public static final float TEMPERATURE_DEFAULT = 0.1f;
    private static final String MODEL_DEFAULT = ChatModel.GPT51.getName();

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

    public static OpenAiChatCompletionRequest makeDefaultImgRequest(String content, String imageUrl, ChatModel model) {
        return new OpenAiChatCompletionRequest()
                .setModel(model.getName())
                .setMessages(PhotoMessageWithPrompt.getMessageList(content, imageUrl));
    }

    public static OpenAiChatCompletionRequest makeModelRequest(
            String content, ChatModel model) {

        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(model.getName())
                .setMessages(PhotoMessageWithPrompt.getFirstMessage(content));
    }

    public static OpenAiChatCompletionRequest makeModelImgRequest(String content, ChatModel model, String imageUrl) {
        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(model.getName())
                .setMessages(PhotoMessageWithPrompt.getMessageList(content, imageUrl));
    }

}
