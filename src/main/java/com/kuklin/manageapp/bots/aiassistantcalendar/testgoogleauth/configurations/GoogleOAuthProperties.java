package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "google.oauth")
public class GoogleOAuthProperties {
    private String clientId;
    private String clientSecret;   // опционально
    private String redirectUri;
    private String authUri;
    private String tokenUri;
    private String revokeUri;
    private String userinfoUri;
    private List<String> scopes;
}
