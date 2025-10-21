package com.kuklin.manageapp.aiconversation.models.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatRole {
    USER("user"), ASSISTANT("assistant"), MODEL("model");

    private String value;
}
