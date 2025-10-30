package com.kuklin.manageapp.bots.aiassistantcalendar.testgoogleauth.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class CryptoConfig {
    @Bean
    @ConfigurationProperties(prefix = "security.crypto")
    public CryptoProps cryptoProps() { return new CryptoProps(); }

    @Bean
    public SecretKey aesKey(CryptoProps props) {
        byte[] key = Base64.getDecoder().decode(props.getAesKeyB64());
        if (key.length != 32) throw new IllegalArgumentException("AES key must be 32 bytes (256-bit)");
        return new SecretKeySpec(key, "AES");
    }

    @lombok.Data
    public static class CryptoProps {
        private String aesKeyB64;
    }
}
