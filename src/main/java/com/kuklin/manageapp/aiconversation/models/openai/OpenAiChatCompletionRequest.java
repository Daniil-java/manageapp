package com.kuklin.manageapp.aiconversation.models.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    // JSON-режим: модель гарантированно отвечает одним JSON-объектом. null — обычный текстовый ответ
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    public static final float TEMPERATURE_DEFAULT = 0.1f;
    // Для живого текста (дневной отчёт): на 0.1 модель на похожих данных выдаёт почти одно и то же,
    // а на 0.9 иногда срывается — вставляла русские слова в английский ответ
    public static final float TEMPERATURE_CREATIVE = 0.8f;
    private static final String MODEL_DEFAULT = ChatModel.GPT51.getName();

    /**
     * json_object — любой JSON-объект; json_schema — ответ строго по схеме (Structured Outputs).
     */
    public record ResponseFormat(
            String type,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonProperty("json_schema")
            JsonSchema jsonSchema) {
        public static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object", null);

        /**
         * Ответ строго по схеме. Корень схемы — объект; strict требует перечислить все поля в required
         * и запретить лишние (additionalProperties: false).
         */
        public static ResponseFormat makeJsonSchema(String name, Object schema) {
            return new ResponseFormat("json_schema", new JsonSchema(name, true, schema));
        }
    }

    public record JsonSchema(String name, boolean strict, Object schema) {
    }

    /**
     * Текстовый запрос в JSON-режиме. В промпте обязательно должно быть слово "JSON" — требование OpenAI.
     */
    public static OpenAiChatCompletionRequest makeJsonRequest(String content) {
        return makeDefaultRequest(content)
                .setResponseFormat(ResponseFormat.JSON_OBJECT);
    }

    /**
     * Текстовый запрос с высокой температурой — для текстов, которые не должны повторяться изо дня в день.
     */
    public static OpenAiChatCompletionRequest makeCreativeRequest(String content) {
        return makeDefaultRequest(content)
                .setTemperature(TEMPERATURE_CREATIVE);
    }

    /**
     * JSON-режим с высокой температурой — тексты по похожим заданиям не звучат одинаково.
     */
    public static OpenAiChatCompletionRequest makeCreativeJsonRequest(String content) {
        return makeCreativeRequest(content)
                .setResponseFormat(ResponseFormat.JSON_OBJECT);
    }

    public static OpenAiChatCompletionRequest makeDefaultRequest(
            String content) {

        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(MODEL_DEFAULT)
                .setMessages(PhotoMessageWithPrompt.getFirstMessage(content));
    }

    /**
     * Инструкции developer-сообщением, данные пользователя (и фото, если есть) — user-сообщением,
     * ответ — по JSON-схеме.
     *
     * @param imageUrl фото или null
     */
    public static OpenAiChatCompletionRequest makeStructuredRequest(
            String instructions, String content, String imageUrl, ResponseFormat responseFormat) {
        return new OpenAiChatCompletionRequest()
                .setTemperature(TEMPERATURE_DEFAULT)
                .setModel(MODEL_DEFAULT)
                .setMessages(PhotoMessageWithPrompt.getMessageListWithInstructions(instructions, content, imageUrl))
                .setResponseFormat(responseFormat);
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
