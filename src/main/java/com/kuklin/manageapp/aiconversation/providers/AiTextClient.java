package com.kuklin.manageapp.aiconversation.providers;

import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;

public interface AiTextClient {
    String fetchResponse(
            String aiKey,
            String content,
            BotIdentifier botIdentifier,
            String uniqLog
    );
}
