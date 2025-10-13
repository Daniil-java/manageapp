package com.kuklin.manageapp.common.library.models.openai;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Usage {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
}
