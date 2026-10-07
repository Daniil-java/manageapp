package com.kuklin.manageapp.bots.caloriebot.configurations;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Calorie Bot API",
                version = "1.0",
                description = "API для управления питанием через Telegram Mini App"
        )
)
public class OpenApiConfig {

    @PostConstruct
    public void init() {
        // КРИТИЧНО: Это исправляет ошибку 500.
        // Говорим Swagger игнорировать параметры, помеченные @AuthenticationPrincipal
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticationPrincipal.class);
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList("TelegramAuth"))
                .components(new Components()
                        .addSecuritySchemes("TelegramAuth", new SecurityScheme()
                                .name("X-TG-INIT-DATA")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Данные инициализации из Telegram WebApp (initData)")));
    }
}