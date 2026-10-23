package com.kuklin.manageapp.aiconversation.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuklin.manageapp.aiconversation.models.enums.AiModelPrice;
import com.kuklin.manageapp.aiconversation.models.enums.AiOperation;
import com.kuklin.manageapp.aiconversation.models.openai.OpenAiChatCompletionRequest;
import com.kuklin.manageapp.aiconversation.models.openai.Usage;
import com.kuklin.manageapp.common.library.models.TranscriptionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Учёт расходов на ИИ: цена по модели, кэшированные токены, контекст пользователя, разбор usage из ответов OpenAI.
 */
class AiUsageAccountingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultModelHasPrice() {
        String model = OpenAiChatCompletionRequest.makeDefaultRequest("x").getModel();

        assertThat(AiModelPrice.findByModel(model)).isPresent();
        assertThat(AiModelPrice.findByModel("gpt-4o-mini-transcribe")).isPresent();
        assertThat(AiModelPrice.findByModel("unknown-model")).isEmpty();
        assertThat(AiModelPrice.findByModel(null)).isEmpty();
    }

    @Test
    void costCountsCachedInputCheaper() {
        // gpt-5.1: 1000 вход (из них 400 из кэша) и 500 выход
        // 600 × 1.25 + 400 × 0.125 + 500 × 10 = 750 + 50 + 5000 = 5800 / 1 000 000
        BigDecimal cost = AiModelPrice.GPT51.getCostUsd(1000, 400, 500);

        assertThat(cost).isEqualByComparingTo("0.0058");
    }

    @Test
    void cachedTokensNeverExceedInput() {
        assertThat(AiModelPrice.GPT51.getCostUsd(100, 500, 0))
                .isEqualByComparingTo(AiModelPrice.GPT51.getCostUsd(100, 100, 0));
    }

    @Test
    void contextGivesUserAndOperationOnlyInsideBlock() {
        assertThat(AiUsageContext.getCurrentAppUserIdOrNull()).isNull();

        try (var ignored = AiUsageContext.open(42L, AiOperation.DISH_PHOTO)) {
            assertThat(AiUsageContext.getCurrentAppUserIdOrNull()).isEqualTo(42L);
            assertThat(AiUsageContext.getCurrentOperationOrNull()).isEqualTo(AiOperation.DISH_PHOTO);
        }

        assertThat(AiUsageContext.getCurrentAppUserIdOrNull()).isNull();
        assertThat(AiUsageContext.getCurrentOperationOrNull()).isNull();
    }

    @Test
    void outerContextWinsOverNested() {
        try (var outer = AiUsageContext.open(7L, AiOperation.DISH_VOICE)) {
            try (var nested = AiUsageContext.open(7L, AiOperation.DISH_TEXT)) {
                assertThat(AiUsageContext.getCurrentOperationOrNull()).isEqualTo(AiOperation.DISH_VOICE);
            }
            // Вложенный close не снимает внешний контекст
            assertThat(AiUsageContext.getCurrentOperationOrNull()).isEqualTo(AiOperation.DISH_VOICE);
        }
        assertThat(AiUsageContext.getCurrentOperationOrNull()).isNull();
    }

    @Test
    void chatUsageReadsCachedTokens() throws Exception {
        Usage usage = mapper.readValue("""
                {"prompt_tokens": 1200, "completion_tokens": 300, "total_tokens": 1500,
                 "prompt_tokens_details": {"cached_tokens": 1024, "audio_tokens": 0}}
                """, Usage.class);

        assertThat(usage.getPromptTokens()).isEqualTo(1200L);
        assertThat(usage.getCachedTokensOrZero()).isEqualTo(1024L);
    }

    @Test
    void chatUsageWithoutDetailsHasZeroCached() throws Exception {
        Usage usage = mapper.readValue("""
                {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                """, Usage.class);

        assertThat(usage.getCachedTokensOrZero()).isZero();
    }

    @Test
    void transcriptionUsageIsParsed() throws Exception {
        TranscriptionResponse response = mapper.readValue("""
                {"text": "кофе, банан",
                 "usage": {"type": "tokens", "input_tokens": 120, "output_tokens": 6, "total_tokens": 126,
                           "input_token_details": {"text_tokens": 40, "audio_tokens": 80}}}
                """, TranscriptionResponse.class);

        assertThat(response.getText()).isEqualTo("кофе, банан");
        assertThat(response.getUsage().getInputTokens()).isEqualTo(120L);
        assertThat(response.getUsage().getOutputTokens()).isEqualTo(6L);
    }
}
