package com.kuklin.manageapp.aiconversation.models.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProviderVariant {
    OPENAI(ChatRole.ASSISTANT),
    GEMINI(ChatRole.MODEL),
    CLAUDE(ChatRole.ASSISTANT),
    DEEPSEEK(ChatRole.ASSISTANT);

    private ChatRole assistant;
}
