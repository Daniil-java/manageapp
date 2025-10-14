package com.kuklin.manageapp.common.library.models.openai;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
@Getter
public enum ChatModel {
    GPT4O("gpt-4o"),
    GPT4O_MINI("gpt-4o-mini"),
    GPT4_1("gpt-4.1"),
    GPT4_1_MINI("gpt-4.1-mini"),
    GPT5("gpt-5"),
    GPT5_MINI("gpt-5-mini"),
    GPT5_NANO("gpt-5-nano");

    private final String name;

    public static List<ChatModel> getModels() {
        return List.of(values());
    }
}
