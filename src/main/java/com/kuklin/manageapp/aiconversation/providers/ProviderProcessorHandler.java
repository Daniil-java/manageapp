package com.kuklin.manageapp.aiconversation.providers;

import com.kuklin.manageapp.aiconversation.models.enums.ProviderVariant;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProviderProcessorHandler {
    private Map<ProviderVariant, ProviderProcessor> map = new ConcurrentHashMap<>();

    public void register(ProviderVariant providerVariant, ProviderProcessor providerProcessor) {
        map.put(providerVariant, providerProcessor);
    }

    public ProviderProcessor getProvider(ProviderVariant providerVariant) {
        return map.get(providerVariant);
    }

    public Map<ProviderVariant, ProviderProcessor> getMap() {
        return map;
    }
}
