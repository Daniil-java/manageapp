package com.kuklin.manageapp.aiconversation.providers;

import com.kuklin.manageapp.aiconversation.models.enums.AiMessageType;
import com.kuklin.manageapp.common.telegram.BotIdentifier;

public interface AiTextClient {
    String fetchResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            AiMessageType messageType);
}
