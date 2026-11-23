package com.kuklin.manageapp.bots.aiassistantcalendar.configurations.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "google.oauth")
// Проверка при старте: значение пропало из application.yaml — приложение не запустится
@Validated
public class GoogleOAuthProperties {
    @NotBlank
    private String clientId;
    private String clientSecret;   // опционально
    @NotBlank
    private String redirectUri;
    @NotBlank
    private String startUri;
    @NotBlank
    private String authUri;
    @NotBlank
    private String tokenUri;
    @NotBlank
    private String revokeUri;
    @NotBlank
    private String userinfoUri;
    @NotEmpty
    private List<String> scopes;
}
