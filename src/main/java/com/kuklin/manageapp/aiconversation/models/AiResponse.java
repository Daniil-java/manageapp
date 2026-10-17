package com.kuklin.manageapp.aiconversation.models;

import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class AiResponse {
    private String content;
    private ChatModel model;
}
