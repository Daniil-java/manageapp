package com.kuklin.manageapp.aiconversation.models.yandex;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class YandexAiMessage {

    private String role;
    private String text;
}
