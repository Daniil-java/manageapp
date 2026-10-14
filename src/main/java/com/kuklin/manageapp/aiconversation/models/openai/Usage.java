package com.kuklin.manageapp.aiconversation.models.openai;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Usage {
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
}
