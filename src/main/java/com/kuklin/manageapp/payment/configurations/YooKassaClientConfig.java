package com.kuklin.manageapp.payment.configurations;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

//Конфиг для доступа к ЮКассе, без телеграмма
public class YooKassaClientConfig {

    @Bean
    public RequestInterceptor yooAuthInterceptor(TelegramPaymentBotKeyComponents components) {
        String basic = components.getShopId() + ":" + components.getShopSecretKey();
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString(basic.getBytes(StandardCharsets.UTF_8));

        return template -> {
            template.header(HttpHeaders.AUTHORIZATION, auth);
            template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            template.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        };
    }
}
