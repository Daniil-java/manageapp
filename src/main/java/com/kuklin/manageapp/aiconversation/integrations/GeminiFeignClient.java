package com.kuklin.manageapp.aiconversation.integrations;

import com.kuklin.manageapp.aiconversation.models.gemini.GeminiRequest;
import com.kuklin.manageapp.aiconversation.models.gemini.GeminiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        value = "gemini-feign-client",
        url = "https://generativelanguage.googleapis.com/v1beta/models"
)
public interface GeminiFeignClient {

    @PostMapping(path = "/{model}:generateContent", consumes = "application/json")
    GeminiResponse generate(@PathVariable("model") String model,
                            @RequestParam("key") String apiKey,
                            @RequestBody GeminiRequest request);
}
