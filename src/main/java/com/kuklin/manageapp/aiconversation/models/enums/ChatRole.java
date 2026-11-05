package com.kuklin.manageapp.aiconversation.models.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatRole {
    // DEVELOPER — инструкции OpenAI: приоритет выше сообщения пользователя
    USER("user"), ASSISTANT("assistant"), MODEL("model"), DEVELOPER("developer");

    private String value;
}
