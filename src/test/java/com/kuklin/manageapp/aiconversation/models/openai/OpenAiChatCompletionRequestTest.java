package com.kuklin.manageapp.aiconversation.models.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON-режим включается только явно и не меняет обычные запросы.
 */
class OpenAiChatCompletionRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultRequestHasNoResponseFormat() throws Exception {
        JsonNode json = mapper.valueToTree(OpenAiChatCompletionRequest.makeDefaultRequest("Hello"));

        assertThat(json.has("response_format")).isFalse();
        assertThat(json.has("responseFormat")).isFalse();
    }

    @Test
    void jsonRequestAsksForJsonObject() throws Exception {
        JsonNode json = mapper.valueToTree(OpenAiChatCompletionRequest.makeJsonRequest("Answer in JSON"));

        assertThat(json.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(json.has("responseFormat")).isFalse();
    }

    @Test
    void jsonRequestKeepsDefaultModelAndMessages() {
        OpenAiChatCompletionRequest plain = OpenAiChatCompletionRequest.makeDefaultRequest("Answer in JSON");
        OpenAiChatCompletionRequest json = OpenAiChatCompletionRequest.makeJsonRequest("Answer in JSON");

        assertThat(json.getModel()).isEqualTo(plain.getModel());
        assertThat(json.getTemperature()).isEqualTo(plain.getTemperature());
        assertThat(mapper.valueToTree(json.getMessages()).toString())
                .isEqualTo(mapper.valueToTree(plain.getMessages()).toString());
    }
}
