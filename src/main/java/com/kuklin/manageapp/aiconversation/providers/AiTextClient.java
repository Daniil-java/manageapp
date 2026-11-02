package com.kuklin.manageapp.aiconversation.providers;

import com.kuklin.manageapp.bots.metrics.entities.MetricsAiInteractionRecord;
import com.kuklin.manageapp.common.telegram.BotIdentifier;

public interface AiTextClient {
    String fetchResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog,
            MetricsAiInteractionRecord.AiMessageType messageType);
}
