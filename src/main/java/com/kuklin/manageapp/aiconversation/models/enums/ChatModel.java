package com.kuklin.manageapp.aiconversation.models.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public enum ChatModel {
    GPT4O("gpt-4o", ProviderVariant.OPENAI),
    GPT5("gpt-5", ProviderVariant.OPENAI),
    GPT51("gpt-5.1", ProviderVariant.OPENAI),
    GEMINI25PRO("gemini-2.5-pro", ProviderVariant.GEMINI),
    GEMINI25FLASH("gemini-2.5-flash", ProviderVariant.GEMINI),
    // Устарели и больше не поддерживаются — не использовать для новых запросов.
    // Оставлены, чтобы читались старые записи dish_choice_chat_model (enum хранится строкой).
    @Deprecated
    CLAUDESONNET45("claude-sonnet-4-5-20250929", ProviderVariant.CLAUDE),
    @Deprecated
    CLAUDEHAIKU45("claude-haiku-4-5-20251001", ProviderVariant.CLAUDE),
    CLAUDESONNET55("claude-sonnet-5-5", ProviderVariant.CLAUDE),
    CLAUDEHAIKU55("claude-haiku-5-5", ProviderVariant.CLAUDE),
//    DEEPSEEKV32EXP("deepseek-ai/DeepSeek-V3.2-Exp", ProviderVariant.DEEPSEEK),
    ;

    private final String name;
    private final ProviderVariant providerVariant;

    public static List<ChatModel> getModels() {
        return List.of(values());
    }
}
