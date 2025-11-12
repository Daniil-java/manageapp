package com.kuklin.manageapp.bots.payment.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class YooKassaClientConfig {

    @Bean
    public WebClient yooWebClient(TelegramPaymentBotKeyComponents components) {
        String basic = components.getShopId() + ":" + components.getShopSecretKey();
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString(basic.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl("https://api.yookassa.ru")
                .defaultHeader(HttpHeaders.AUTHORIZATION, auth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

}
