package com.kuklin.manageapp.aiconversation.models.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public enum ChatModel {
    GPT4O("gpt-4o", ProviderVariant.OPENAI),
    GPT5("gpt-5", ProviderVariant.OPENAI),
    GEMINI25PRO("gemini-2.5-pro", ProviderVariant.GEMINI),
    GEMINI25FLASH("gemini-2.5-flash", ProviderVariant.GEMINI),
    CLAUDESONNET45("claude-sonnet-4-5-20250929", ProviderVariant.CLAUDE),
    CLAUDEHAIKU45("claude-haiku-4-5-20251001", ProviderVariant.CLAUDE),
//    DEEPSEEKV32EXP("deepseek-ai/DeepSeek-V3.2-Exp", ProviderVariant.DEEPSEEK),
    ;

    private final String name;
    private final ProviderVariant providerVariant;

    public static List<ChatModel> getModels() {
        return List.of(values());
    }
}
