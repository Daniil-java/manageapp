package com.kuklin.manageapp.aiconversation.integrations;

import com.kuklin.manageapp.aiconversation.models.claude.ClaudeRequest;
import com.kuklin.manageapp.aiconversation.models.claude.ClaudeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        value = "anthropic-feign-client",
        url = "https://api.anthropic.com/v1"
)
public interface ClaudeFeignClient {

    @PostMapping(
            value = "/messages",
            consumes = "application/json",
            produces = "application/json"
    )
    ClaudeResponse messages(
            @RequestHeader("x-api-key") String apiKey,
            @RequestHeader("anthropic-version") String apiVersion // напр. "2023-06-01"
            , @RequestBody ClaudeRequest request
    );
}
