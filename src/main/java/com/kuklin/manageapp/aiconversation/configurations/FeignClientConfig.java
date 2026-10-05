package com.kuklin.manageapp.aiconversation.configurations;

import feign.codec.Encoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class FeignClientConfig {
    @Bean
    public Encoder feignEncoder() {
        return new SpringEncoder(() -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter()));
    }

}
