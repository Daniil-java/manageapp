package com.kuklin.manageapp.aiconversation.providers.impl;

import com.kuklin.manageapp.aiconversation.integrations.OpenAiFeignClient;
import com.kuklin.manageapp.aiconversation.models.AiUnavailableException;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionResponse;
import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiInteractionRecordService;
import com.kuklin.manageapp.bots.metrics.services.MetricsAiLogService;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ошибка OpenAI (401, 5xx, пустой ответ) — AiUnavailableException с кодом, а не FeignException или NPE:
 * по нему боты говорят пользователю «ИИ сейчас не отвечает, попробуйте позже».
 */
class OpenAiProviderProcessorErrorsTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST, "https://api.openai.com/v1/chat/completions",
            Map.of(), null, StandardCharsets.UTF_8, null);

    private OpenAiFeignClient client;
    private OpenAiProviderProcessor processor;

    @BeforeEach
    void setUp() {
        client = mock(OpenAiFeignClient.class);
        processor = new OpenAiProviderProcessor(client, mock(MetricsAiLogService.class),
                mock(MetricsAiInteractionRecordService.class));
    }

    @Test
    void unauthorizedBecomesAiUnavailableWithStatus() {
        when(client.generate(anyString(), any())).thenThrow(
                new FeignException.Unauthorized("Incorrect API key", REQUEST, null, null));

        assertThatThrownBy(this::fetchText)
                .isInstanceOf(AiUnavailableException.class)
                .extracting(e -> ((AiUnavailableException) e).getStatus())
                .isEqualTo(401);
    }

    @Test
    void serverErrorBecomesAiUnavailable() {
        when(client.generate(anyString(), any())).thenThrow(
                new FeignException.InternalServerError("boom", REQUEST, null, null));

        assertThatThrownBy(this::fetchText)
                .isInstanceOf(AiUnavailableException.class)
                .extracting(e -> ((AiUnavailableException) e).getStatus())
                .isEqualTo(500);
    }

    @Test
    void emptyResponseBecomesAiUnavailable() {
        when(client.generate(anyString(), any())).thenReturn(new OpenAiChatCompletionResponse().setChoices(List.of()));

        assertThatThrownBy(this::fetchText).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void photoErrorBecomesAiUnavailable() {
        when(client.generate(anyString(), any())).thenThrow(
                new FeignException.TooManyRequests("quota", REQUEST, null, null));

        assertThatThrownBy(() -> processor.fetchPhotoResponse("key", "prompt", "data:image/jpeg;base64,AA", BotIdentifier.CALORIE_BOT))
                .isInstanceOf(AiUnavailableException.class)
                .extracting(e -> ((AiUnavailableException) e).getStatus())
                .isEqualTo(429);
    }

    @Test
    void transcriptionErrorBecomesAiUnavailable() {
        when(client.transcribeAudioWithPrompt(anyString(), any(), anyString(), anyString())).thenThrow(
                new FeignException.Unauthorized("Incorrect API key", REQUEST, null, null));

        assertThatThrownBy(() -> processor.fetchAudioResponse("key", new byte[]{1}, "audio.ogg", "audio/ogg",
                "еда", BotIdentifier.CALORIE_BOT, "test"))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void transcriptionWithoutTextBecomesAiUnavailable() {
        when(client.transcribeAudio(anyString(), any(), anyString())).thenReturn(new TranscriptionResponse());

        assertThatThrownBy(() -> processor.fetchAudioResponse("key", new byte[]{1}, BotIdentifier.CALORIE_BOT, "test"))
                .isInstanceOf(AiUnavailableException.class)
                .extracting(e -> ((AiUnavailableException) e).getStatus())
                .isEqualTo(0);
    }

    private String fetchText() {
        return processor.fetchResponse("key", "prompt", BotIdentifier.CALORIE_BOT, "test",
                MetricsAiInteractionRecord.AiMessageType.TEXT);
    }
}
