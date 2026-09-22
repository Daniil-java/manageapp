package com.kuklin.manageapp.aiconversation.providers;

import com.kuklin.manageapp.aiconversation.models.AiResponse;
import com.kuklin.manageapp.aiconversation.models.enums.ChatModel;
import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import com.kuklin.manageapp.common.library.tgutils.BotIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

public interface ProviderProcessor {
    AiResponse fetchResponsePhotoOrNull(
            String imagerUrl,
            String content,
            ChatModel chatModel,
            String aiKey,
            BotIdentifier botIdentifier,
            String uniqLog
    );

    ProviderVariant getProviderName();

    @Autowired
    default void registerMyself(ProviderProcessorHandler providerProcessorHandler) {
        providerProcessorHandler.register(getProviderName(), this);
    }
}
