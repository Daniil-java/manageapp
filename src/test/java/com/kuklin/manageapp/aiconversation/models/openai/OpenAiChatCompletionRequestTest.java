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
        assertThat(json.path("response_format").has("json_schema")).isFalse();
        assertThat(json.has("responseFormat")).isFalse();
    }

    @Test
    void structuredRequestSplitsInstructionsAndUserMessage() {
        Object schema = mapper.createObjectNode().put("type", "object");
        JsonNode json = mapper.valueToTree(OpenAiChatCompletionRequest.makeStructuredRequest(
                "Rules", "User text", "data:image/jpeg;base64,AA",
                OpenAiChatCompletionRequest.ResponseFormat.makeJsonSchema("dishes", schema)));

        JsonNode messages = json.path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("developer");
        assertThat(messages.get(0).path("content").get(0).path("text").asText()).isEqualTo("Rules");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").get(0).path("text").asText()).isEqualTo("User text");
        assertThat(messages.get(1).path("content").get(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/jpeg;base64,AA");

        JsonNode format = json.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("name").asText()).isEqualTo("dishes");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("schema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void structuredTextRequestHasNoImage() {
        JsonNode json = mapper.valueToTree(OpenAiChatCompletionRequest.makeStructuredRequest(
                "Rules", "User text", null, OpenAiChatCompletionRequest.ResponseFormat.JSON_OBJECT));

        assertThat(json.path("messages").get(1).path("content")).hasSize(1);
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
